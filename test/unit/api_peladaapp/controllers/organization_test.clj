(ns api-peladaapp.controllers.organization-test
  (:require
   [api-peladaapp.controllers.organization :as controller.org]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.organization-invitation :as db.invitation]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.waha :as waha]
   [clojure.test :refer [deftest is testing]]
   [next.jdbc :as jdbc]))

(deftest test-get-organization-not-found
  (let [db "dummy-db"
        org-uuid (random-uuid)]
    (testing "get-organization throws when not found"
      (with-redefs [db.organization/get-organization (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Organization not found"
                              (controller.org/get-organization org-uuid db)))))))

(deftest test-update-organization-not-found
  (let [db "dummy-db"
        org-uuid (random-uuid)]
    (testing "update-organization throws when not found"
      (with-redefs [db.organization/update-organization (fn [_ _ _] 0)]
        (is (thrown-with-msg? Exception #"Organization not found"
                              (controller.org/update-organization org-uuid {} db)))))))

(deftest test-delete-organization-not-found
  (let [db "dummy-db"
        org-uuid (random-uuid)]
    (testing "delete-organization throws when not found"
      (with-redefs [db.organization/delete-organization (fn [_ _] 0)]
        (is (thrown-with-msg? Exception #"Organization not found"
                              (controller.org/delete-organization org-uuid db)))))))

(deftest test-test-waha-connection
  (let [db "dummy-db"
        org-uuid (random-uuid)]
    (testing "throws when waha not enabled"
      (with-redefs [db.organization/get-organization (fn [_ _] {:id org-uuid :waha-enabled false})]
        (is (thrown-with-msg? Exception #"WAHA não está habilitado"
                              (controller.org/test-waha-connection org-uuid db)))))

    (testing "throws when waha returns error"
      (with-redefs [db.organization/get-organization (fn [_ _] {:id org-uuid :waha-enabled true})
                    waha/send-message (fn [_ _] {:error "Connection timeout"})]
        (is (thrown-with-msg? Exception #"Erro no WAHA: Connection timeout"
                              (controller.org/test-waha-connection org-uuid db)))))

    (testing "succeeds on waha success"
      (with-redefs [db.organization/get-organization (fn [_ _] {:id org-uuid :waha-enabled true})
                    waha/send-message (fn [_ _] {:status "ok"})]
        (is (= "success" (:status (controller.org/test-waha-connection org-uuid db))))))))

(deftest test-leave-organization-controller
  (let [db "dummy-db"
        org-uuid (random-uuid)
        user-uuid (random-uuid)]
    (testing "throws when user is the last admin of organization"
      (with-redefs [jdbc/transact (fn [db f & _] (f db))
                    db.admin/is-user-admin-of-organization? (fn [_ _ _] true)
                    db.admin/count-admins-by-organization (fn [_ _] 1)
                    db.player/get-org-player-by-user-id (fn [_ _ _] {:id 1})]
        (is (thrown-with-msg? Exception #"Cannot leave organization: you are the last administrator"
                              (controller.org/leave-organization org-uuid user-uuid db)))))

    (testing "succeeds when not the last admin"
      (let [deleted-admin (atom false)
            deleted-player (atom false)]
        (with-redefs [jdbc/transact (fn [db f & _] (f db))
                      db.admin/is-user-admin-of-organization? (fn [_ _ _] true)
                      db.admin/count-admins-by-organization (fn [_ _] 2)
                      db.player/get-org-player-by-user-id (fn [_ _ _] {:id 456})
                      db.admin/delete-organization-admin-by-org-and-user (fn [_ _ _] (reset! deleted-admin true))
                      db.player/delete-player (fn [id _] (is (= 456 id)) (reset! deleted-player true))]
          (controller.org/leave-organization org-uuid user-uuid db)
          (is @deleted-admin)
          (is @deleted-player))))))

(deftest test-accept-invitation-edgecases
  (let [db "dummy-db"
        user-uuid (random-uuid)
        org-uuid (random-uuid)
        token "test-token"]
    (testing "throws if invitation token is not found"
      (with-redefs [db.invitation/get-invitation-by-token (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Invitation not found"
                              (controller.org/accept-invitation token user-uuid db)))))

    (testing "throws if invitation belongs to another user (guest mismatch)"
      (with-redefs [db.invitation/get-invitation-by-token (fn [_ _] {:organization-id org-uuid :email (str "guest-" (random-uuid))})
                    db.player/get-org-player-by-user-id (fn [_ _ _] nil)
                    db.user/find-user-by-id (fn [_ _] {:id user-uuid :email "u@e.com" :username "u"})]
        (is (thrown-with-msg? Exception #"Invitation does not belong to this user"
                              (controller.org/accept-invitation token user-uuid db)))))

    (testing "throws if invitation belongs to another user (email mismatch)"
      (with-redefs [db.invitation/get-invitation-by-token (fn [_ _] {:organization-id org-uuid :email "other@e.com"})
                    db.player/get-org-player-by-user-id (fn [_ _ _] nil)
                    db.user/find-user-by-id (fn [_ _] {:id user-uuid :email "me@e.com" :username "me"})]
        (is (thrown-with-msg? Exception #"Invitation does not belong to this user"
                              (controller.org/accept-invitation token user-uuid db)))))

    (testing "throws if organization free tier limit is reached"
      (with-redefs [db.invitation/get-invitation-by-token (fn [_ _] {:organization-id org-uuid :email "me@e.com"})
                    db.player/get-org-player-by-user-id (fn [_ _ _] nil)
                    db.user/find-user-by-id (fn [_ _] {:id user-uuid :email "me@e.com" :username "me"})
                    db.organization/get-organization-feature-flags (fn [_ _] {:unlimited_members false})
                    db.player/count-players-by-org (fn [_ _] 15)]
        (is (thrown-with-msg? Exception #"Limite de membros atingido"
                              (controller.org/accept-invitation token user-uuid db)))))))
