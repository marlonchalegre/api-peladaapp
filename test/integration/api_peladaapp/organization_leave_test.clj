(ns integration.api-peladaapp.organization-leave-test
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as helpers]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock]))

(use-fixtures :each helpers/test-system-fixture)

(defn- exec-one! [ds query]
  (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest leave-organization-test
  (let [db-val (-> helpers/*test-system* :database :database)
        db (if (fn? db-val) (db-val) db-val)
        app (-> helpers/*test-system* :app :app-handler)

        ;; Register user 1 (Admin)
        token1 (helpers/register-and-login! app {:name "Admin One" :email "admin1@test.com" :password "pass123"})
        user1-id (helpers/user-id-by-email db "admin1@test.com")

        ;; Create organization (user1 becomes admin and player)
        create-resp (app (-> (mock/request :post "/api/organizations")
                             (helpers/auth-cookie token1)
                             (mock/json-body {:name "Test Org"})))
        org (helpers/decode-body create-resp)
        org-id (:id org)

        ;; Register user 2 (Player)
        token2 (helpers/register-and-login! app {:name "Player Two" :email "player2@test.com" :password "pass123"})
        user2-id (helpers/user-id-by-email db "player2@test.com")]

    ;; Add user 2 to org
    (exec-one! db (-> (h/insert-into :OrganizationPlayers) (h/values [{:user_id user2-id :organization_id org-id :grade 5.0}])))

    ;; Verify initial state
    (is (= 200 (:status (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                 (helpers/auth-cookie token2)))))
        "Player should be member of org")

    ;; 1. Player leaves organization
    (let [leave-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/leave"))
                              (helpers/auth-cookie token2)))]
      (is (= 200 (:status leave-resp)) "Player should leave successfully")
      (is (= 403 (:status (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                   (helpers/auth-cookie token2)))))
          "Player should no longer have access")

      ;; Verify in DB
      (is (empty? (exec! db (-> (h/select :*) (h/from :OrganizationPlayers) (h/where [:= :user_id user2-id] [:= :organization_id org-id]))))))

    ;; 2. Last admin tries to leave
    (let [leave-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/leave"))
                              (helpers/auth-cookie token1)))]
      (is (= 400 (:status leave-resp)) "Last admin should not be able to leave")
      (is (str/includes? (str (:body leave-resp)) "last administrator")))

    ;; 3. Add another admin and then leave
    (let [_ (helpers/register-and-login! app {:name "Admin Three" :email "admin3@test.com" :password "pass123"})
          user3-id (helpers/user-id-by-email db "admin3@test.com")]
      ;; Admin 1 adds Admin 3
      (app (-> (mock/request :post (str "/api/organizations/" org-id "/admins"))
               (helpers/auth-cookie token1)
               (mock/json-body {:user_id user3-id})))
      ;; Add as player too
      (exec-one! db (-> (h/insert-into :OrganizationPlayers) (h/values [{:user_id user3-id :organization_id org-id :grade 5.0}])))

      ;; Now Admin 1 can leave
      (let [leave-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/leave"))
                                (helpers/auth-cookie token1)))]
        (is (= 200 (:status leave-resp)) "Non-last admin should be able to leave")
        (is (= 403 (:status (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                     (helpers/auth-cookie token1)))))
            "Former admin should no longer have access")

        ;; Verify in DB
        (is (empty? (exec! db (-> (h/select :*) (h/from :OrganizationAdmins) (h/where [:= :user_id user1-id] [:= :organization_id org-id])))))
        (is (empty? (exec! db (-> (h/select :*) (h/from :OrganizationPlayers) (h/where [:= :user_id user1-id] [:= :organization_id org-id])))))))))
