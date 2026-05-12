(ns api-peladaapp.invitation-test
  (:require
   [api-peladaapp.controllers.auth :as controller.auth]
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.controllers.user :as controller.user]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as helpers]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(use-fixtures :each helpers/test-system-fixture)

(def opts {:builder-fn rs/as-unqualified-lower-maps})

(deftest invite-player-test
  (let [db-val (get-in helpers/*test-system* [:database :database])
        db (if (fn? db-val) (db-val) db-val)
        ;; Create admin user to satisfy foreign key constraints
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                             (h/values [{:id 99 :email "admin@test.com" :username "admin" :name "Admin"}]))) opts)
        org-name "Test Invitation Org"
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Organizations)
                                             (h/values [{:name org-name}]))) opts)
        org-id (-> (jdbc/execute-one! db (hsql/format (-> (h/select :id)
                                                          (h/from :Organizations)
                                                          (h/where [:= :name org-name]))) opts)
                   :id)
        email "new-player@example.com"]

    (testing "Inviting a non-existent user creates a partial user and DOES NOT add to org yet"
      (let [result (controller.organization/invite-player-improved org-id email nil 99 db)]
        (is (= email (:email result)))
        (is (:is-new-user result))
        (is (= org-id (:organization-id result)))

        ;; Verify user exists in DB
        (let [user (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                          (h/from :Users)
                                                          (h/where [:= :email email]))) opts)]
          (is (some? user))
          (is (nil? (:password user))))

        ;; Verify player NOT added to org yet
        (let [player (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                            (h/from :OrganizationPlayers)
                                                            (h/where [:= :user_id (:user-id result)]
                                                                     [:= :organization_id org-id]))) opts)]
          (is (nil? player)))))

    (testing "Inviting an existing user DOES NOT add them to org automatically (requires acceptance)"
      (let [existing-email "existing@example.com"
            _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                                 (h/values [{:email existing-email :name "Existing" :password "hashed-pass"}]))) opts)
            user-id (helpers/user-id-by-email db existing-email)
            result (controller.organization/invite-player-improved org-id existing-email nil 99 db)]
        (is (= existing-email (:email result)))
        (is (not (:is-new-user result)))
        (is (= user-id (:user-id result)))

        ;; Verify player NOT added to org yet
        (let [player (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                            (h/from :OrganizationPlayers)
                                                            (h/where [:= :user_id user-id]
                                                                     [:= :organization_id org-id]))) opts)]
          (is (nil? player)))))

    (testing "Inviting by name only DOES NOT add user to org automatically (requires acceptance)"
      (let [name "Guest Player"
            result (controller.organization/invite-player-improved org-id nil name 99 db)]
        (is (= name (:name result)))
        (is (nil? (:email result)))

        ;; Verify user exists in DB
        (let [user (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                          (h/from :Users)
                                                          (h/where [:= :name name]))) opts)]
          (is (some? user)))

        ;; Verify player NOT ADDED to org yet
        (let [player (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                            (h/from :OrganizationPlayers)
                                                            (h/where [:= :user_id (:user-id result)]
                                                                     [:= :organization_id org-id]))) opts)]
          (is (nil? player)))))))

(deftest first-access-test
  (let [db-val (get-in helpers/*test-system* [:database :database])
        db (if (fn? db-val) (db-val) db-val)
        ;; Create admin user to satisfy foreign key constraints
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                             (h/values [{:id 99 :email "admin@test.com" :username "admin" :name "Admin"}]))) opts)
        email "invited@example.com"
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Organizations)
                                             (h/values [{:name "First Access Org"}]))) opts)
        org-id (-> (jdbc/execute-one! db (hsql/format (-> (h/select :id)
                                                          (h/from :Organizations)
                                                          (h/where [:= :name "First Access Org"]))) opts)
                   :id)
        ;; Use controller to create invite (which also creates partial user)
        _ (controller.organization/invite-player-improved org-id email nil 99 db)
        token (:token (first (controller.organization/list-organization-invitations org-id db)))
        user-id (helpers/user-id-by-email db email)]

    (testing "Completing first access for invited user"
      (let [payload {:email email
                     :username "inviteduser"
                     :name "New User"
                     :password "securepassword"
                     :position "Striker"
                     :token token}
            result (controller.auth/first-access payload db)]
        (is (some? (:token result)))
        (is (= "New User" (get-in result [:user :name])))
        (is (= "inviteduser" (get-in result [:user :username])))
        (is (= "Striker" (get-in result [:user :position])))

        ;; Verify data is stored
        (let [db-user (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                             (h/from :Users)
                                                             (h/where [:= :id user-id]))) opts)]
          (is (= "inviteduser" (:username db-user)))
          (is (some? (:password db-user))))

        ;; Verify player added to org
        (let [player (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                            (h/from :OrganizationPlayers)
                                                            (h/where [:= :user_id user-id]
                                                                     [:= :organization_id org-id]))) opts)]
          (is (some? player)))))

    (testing "Cannot use first access for already registered user"
      (let [_ (jdbc/execute! db (hsql/format (-> (h/insert-into :Organizations)
                                                 (h/values [{:name "First Access Org 2"}]))) opts)
            org-id-2 (-> (jdbc/execute-one! db (hsql/format (-> (h/select :id)
                                                                (h/from :Organizations)
                                                                (h/where [:= :name "First Access Org 2"]))) opts)
                         :id)
            _ (controller.organization/invite-player-improved org-id-2 email nil 99 db)
            token-2 (:token (first (controller.organization/list-organization-invitations org-id-2 db)))
            payload {:email email
                     :username "otheruser"
                     :name "Other Name"
                     :password "another-pass"
                     :token token-2}]
        ;; It should throw because password was set in previous test if it was same DB, 
        ;; but fixture is :each, so it resets. Wait, I should ensure it HAS a password.
        (jdbc/execute! db (hsql/format (-> (h/update :Users)
                                           (h/set {:password "already-set"})
                                           (h/where [:= :email email]))) opts)
        (is (thrown-with-msg? Exception #"already has a password"
                              (controller.auth/first-access payload db)))))))

(deftest link-invitation-test
  (let [db-val (get-in helpers/*test-system* [:database :database])
        db (if (fn? db-val) (db-val) db-val)
        ;; Create admin user to satisfy foreign key constraints
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                             (h/values [{:id 99 :email "admin@test.com" :username "admin" :name "Admin"}]))) opts)
        org-name "Link Org"
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Organizations)
                                             (h/values [{:name org-name}]))) opts)
        org-id (-> (jdbc/execute-one! db (hsql/format (-> (h/select :id)
                                                          (h/from :Organizations)
                                                          (h/where [:= :name org-name]))) opts)
                   :id)
        user-id 99] ;; Mock admin id

    (testing "Creating/Getting a link invitation"
      (let [token (controller.organization/get-or-create-organization-link org-id user-id db)]
        (is (string? token))
        (is (= token (controller.organization/get-or-create-organization-link org-id user-id db)))

        (let [inv (controller.organization/get-invitation-by-token token db)]
          (is (= org-id (:organization-id inv)))
          (is (= org-name (:organization-name inv)))
          (is (nil? (:email inv))))))

    (testing "Accepting a link invitation"
      (let [token (controller.organization/get-or-create-organization-link org-id user-id db)
            new-user-id 100
            _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                                 (h/values [{:id new-user-id :email "tester@test.com" :username "testeruser" :name "Tester"}]))) opts)
            result (controller.organization/accept-invitation token new-user-id db)]
        (is (= org-id (:organization-id result)))

        ;; Verify player added
        (let [player (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                            (h/from :OrganizationPlayers)
                                                            (h/where [:= :user_id new-user-id]
                                                                     [:= :organization_id org-id]))) opts)]
          (is (some? player)))))

    (testing "Listing pending invitations"
      (let [email "invited-user@test.com"
            _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                                 (h/values [{:email email :username "inviteduser" :name "Invited"}]))) opts)
            u-id (helpers/user-id-by-email db email)
            _ (controller.organization/invite-player org-id email user-id db)
            invites (controller.organization/list-pending-invitations email db)]
        (is (= 1 (count invites)))
        (is (= org-name (:organization-name (first invites))))

        ;; Accept it
        (controller.organization/accept-invitation (:token (first invites)) u-id db)
        (is (empty? (controller.organization/list-pending-invitations email db)))))))

(deftest manage-invitations-test
  (let [db-val (get-in helpers/*test-system* [:database :database])
        db (if (fn? db-val) (db-val) db-val)
        ;; Create admin user to satisfy foreign key constraints
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                             (h/values [{:id 99 :email "admin@test.com" :username "admin" :name "Admin"}]))) opts)
        org-name "Manage Invites Org"
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Organizations)
                                             (h/values [{:name org-name}]))) opts)
        org-id (-> (jdbc/execute-one! db (hsql/format (-> (h/select :id)
                                                          (h/from :Organizations)
                                                          (h/where [:= :name org-name]))) opts)
                   :id)
        user-id 99]

    (testing "Listing and revoking invitations"
      (controller.organization/invite-player org-id "to-revoke@test.com" user-id db)
      (let [invites (controller.organization/list-organization-invitations org-id db)]
        (is (= 1 (count invites)))
        (is (= "to-revoke@test.com" (:email (first invites))))

        (let [inv-id (:id (first invites))]
          (controller.organization/revoke-invitation inv-id org-id db)
          (is (empty? (controller.organization/list-organization-invitations org-id db))))))))

(deftest invitation-edge-cases-test
  (let [db-val (get-in helpers/*test-system* [:database :database])
        db (if (fn? db-val) (db-val) db-val)
        ;; Create admin user to satisfy foreign key constraints
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                             (h/values [{:id 99 :email "admin@test.com" :username "admin" :name "Admin"}]))) opts)
        org-name "Edge Case Org"
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Organizations)
                                             (h/values [{:name org-name}]))) opts)
        org-id (-> (jdbc/execute-one! db (hsql/format (-> (h/select :id)
                                                          (h/from :Organizations)
                                                          (h/where [:= :name org-name]))) opts)
                   :id)
        user-id 99]

    (testing "Accepting invalid token throws exception"
      (is (thrown-with-msg? Exception #"Invitation not found"
                            (controller.organization/accept-invitation "invalid-token-uuid" user-id db))))

    (testing "Revoking invitation from another organization does nothing"
      ;; Create another org
      (jdbc/execute! db (hsql/format (-> (h/insert-into :Organizations)
                                         (h/values [{:name "Other Org"}]))) opts)
      (let [other-org-id (-> (jdbc/execute-one! db (hsql/format (-> (h/select :id)
                                                                    (h/from :Organizations)
                                                                    (h/where [:= :name "Other Org"]))) opts)
                             :id)]

        ;; Create invite in main org
        (controller.organization/invite-player org-id "revoke-fail@test.com" user-id db)
        (let [invites (controller.organization/list-organization-invitations org-id db)
              inv-id (:id (first invites))]

          ;; Try to revoke using other-org-id
          (controller.organization/revoke-invitation inv-id other-org-id db)

          ;; Verify it still exists
          (is (= 1 (count (controller.organization/list-organization-invitations org-id db)))))))

    (testing "Security: Cannot accept invitation sent to another email"
      (let [email "secure-invite@test.com"
            _ (controller.organization/invite-player org-id email user-id db)
            invite (first (controller.organization/list-pending-invitations email db))
            token (:token invite)

            ;; Create another user
            attacker-email "attacker@test.com"
            _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                                 (h/values [{:email attacker-email :username "attackeruser" :name "Attacker"}]))) opts)
            attacker-id (helpers/user-id-by-email db attacker-email)]

        (is (thrown-with-msg? Exception #"Invitation does not belong to this user"
                              (controller.organization/accept-invitation token attacker-id db)))))

    (testing "UX: Registration claims partial user"
      (let [email "partial-user@test.com"
            _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                                 (h/values [{:email email}]))) opts) ;; Partial user
            partial-id (helpers/user-id-by-email db email)
            new-user-data {:email email :username "claimeduser" :name "Claimed Name" :password "pass123" :position "Striker"}
            result (controller.user/create-user new-user-data db)
            updated-user (jdbc/execute-one! db (hsql/format (-> (h/select :*)
                                                                (h/from :Users)
                                                                (h/where [:= :id partial-id]))) opts)]
        (is (= partial-id (:id result)))
        (is (= "Claimed Name" (:name updated-user)))
        (is (some? (:password updated-user)))))))

(deftest reset-link-invitation-test
  (let [db-val (get-in helpers/*test-system* [:database :database])
        db (if (fn? db-val) (db-val) db-val)
        ;; Create admin user to satisfy foreign key constraints
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Users)
                                             (h/values [{:id 99 :email "admin@test.com" :username "admin" :name "Admin"}]))) opts)
        org-name "Reset Link Org"
        _ (jdbc/execute! db (hsql/format (-> (h/insert-into :Organizations)
                                             (h/values [{:name org-name}]))) opts)
        org-id (-> (jdbc/execute-one! db (hsql/format (-> (h/select :id)
                                                          (h/from :Organizations)
                                                          (h/where [:= :name org-name]))) opts)
                   :id)
        user-id 99]

    (testing "Resetting a link invitation replaces the old token"
      (let [token1 (controller.organization/get-or-create-organization-link org-id user-id db)
            _ (is (string? token1))
            token2 (controller.organization/reset-organization-link org-id user-id db)]
        (is (string? token2))
        (is (not= token1 token2))

        ;; Verify old token is gone
        (is (thrown-with-msg? Exception #"Invitation not found"
                              (controller.organization/get-invitation-by-token token1 db)))

        ;; Verify new token exists
        (let [inv (controller.organization/get-invitation-by-token token2 db)]
          (is (= org-id (:organization-id inv))))))))
