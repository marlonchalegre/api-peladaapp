(ns integration.api-peladaapp.organization-leave-test
  (:require
   [api-peladaapp.server :as server]
   [api-peladaapp.test-helpers :as helpers]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each helpers/test-system-fixture)

(deftest leave-organization-test
  (let [db-raw (-> helpers/*test-system* :database :database)
        db (if (fn? db-raw) (db-raw) db-raw)
        app (fn [req] (server/app (assoc req :database db)))

        ;; Register user 1 (Admin)
        token1 (helpers/register-and-login! app {:name "Admin One" :email "admin1@test.com" :password "pass123"})
        user1-id (helpers/user-id-by-email db "admin1@test.com")

        ;; Create organization (user1 becomes admin and player)
        create-resp (app (-> (mock/request :post "/api/organizations")
                             (mock/header "Authorization" (str "Token " token1))
                             (mock/json-body {:name "Test Org"})))
        org (helpers/decode-body create-resp)
        org-id (:id org)

        ;; Register user 2 (Player)
        token2 (helpers/register-and-login! app {:name "Player Two" :email "player2@test.com" :password "pass123"})
        user2-id (helpers/user-id-by-email db "player2@test.com")]

    ;; Add user 2 to org
    (jdbc/execute! db ["INSERT INTO OrganizationPlayers (user_id, organization_id, grade) VALUES (?, ?, 5.0)"
                       user2-id org-id])

    ;; Verify initial state
    (is (= 200 (:status (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                 (mock/header "Authorization" (str "Token " token2))))))
        "Player should be member of org")

    ;; 1. Player leaves organization
    (let [leave-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/leave"))
                              (mock/header "Authorization" (str "Token " token2))))]
      (is (= 200 (:status leave-resp)) "Player should leave successfully")
      (is (= 403 (:status (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                   (mock/header "Authorization" (str "Token " token2))))))
          "Player should no longer have access")

      ;; Verify in DB
      (is (empty? (jdbc/execute! db ["SELECT * FROM OrganizationPlayers WHERE user_id = ? AND organization_id = ?" user2-id org-id]))))

    ;; 2. Last admin tries to leave
    (let [leave-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/leave"))
                              (mock/header "Authorization" (str "Token " token1))))]
      (is (= 400 (:status leave-resp)) "Last admin should not be able to leave")
      (is (clojure.string/includes? (str (:body leave-resp)) "last administrator")))

    ;; 3. Add another admin and then leave
    (let [token3 (helpers/register-and-login! app {:name "Admin Three" :email "admin3@test.com" :password "pass123"})
          user3-id (helpers/user-id-by-email db "admin3@test.com")]
      ;; Admin 1 adds Admin 3
      (app (-> (mock/request :post (str "/api/organizations/" org-id "/admins"))
               (mock/header "Authorization" (str "Token " token1))
               (mock/json-body {:user_id user3-id})))
      ;; Add as player too
      (jdbc/execute! db ["INSERT INTO OrganizationPlayers (user_id, organization_id, grade) VALUES (?, ?, 5.0)"
                         user3-id org-id])

      ;; Now Admin 1 can leave
      (let [leave-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/leave"))
                                (mock/header "Authorization" (str "Token " token1))))]
        (is (= 200 (:status leave-resp)) "Non-last admin should be able to leave")
        (is (= 403 (:status (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                     (mock/header "Authorization" (str "Token " token1))))))
            "Former admin should no longer have access")

        ;; Verify in DB
        (is (empty? (jdbc/execute! db ["SELECT * FROM OrganizationAdmins WHERE user_id = ? AND organization_id = ?" user1-id org-id])))
        (is (empty? (jdbc/execute! db ["SELECT * FROM OrganizationPlayers WHERE user_id = ? AND organization_id = ?" user1-id org-id])))))))
