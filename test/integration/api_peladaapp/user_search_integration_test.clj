(ns api-peladaapp.user-search-integration-test
  (:require
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- get-ds []
  (let [db-file (:db-file th/*test-system*)]
    (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})))

(deftest user-search-api-test
  (let [app (-> th/*test-system* :app :handler)
        ds (get-ds)
        admin-token (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "admin123"})
        admin-user-id (th/user-id-by-email ds "admin@test.com")]

    ;; Setup: Create organization and add all users to it
    (let [org-id (-> (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES (?)" "Test Org"])
                     first
                     ((fn [row] (or (:id row) (get row "id") (first (vals row))))))]
      ;; Add admin to org
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id, grade) VALUES (?, ?, ?)" org-id admin-user-id 5.0])

      ;; Seed data and add to org
      (doseq [user [{:name "Cristiano Ronaldo" :email "cr7@test.com"}
                    {:name "Lionel Messi" :email "leo@test.com"}
                    {:name "Neymar Jr" :email "ney@gmail.com"}]]
        (let [user-id (db.user/insert-user (assoc user :password "pass") ds)]
          (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id, grade) VALUES (?, ?, ?)" org-id user-id 5.0]))))

    (testing "GET /api/users/search - search by name"
      (let [resp (app (-> (mock/request :get "/api/users/search")
                          ((th/auth-cookie admin-token))
                          (assoc :query-params {"q" "Messi"})))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1 (count body)))
        (is (= "Lionel Messi" (:name (first body))))))

    (testing "GET /api/users/search - search by email domain"
      (let [resp (app (-> (mock/request :get "/api/users/search")
                          ((th/auth-cookie admin-token))
                          (assoc :query-params {"q" "@test.com"})))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        ;; Admin + Cristiano + Lionel
        (is (= 3 (count body)))))

    (testing "GET /api/users/search - pagination headers"
      (let [resp (app (-> (mock/request :get "/api/users/search")
                          ((th/auth-cookie admin-token))
                          (assoc :query-params {"q" "" "per_page" "2"})))]
        (is (= 200 (:status resp)))
        ;; Total should be 4 (3 seeded + 1 admin)
        (is (= "4" (get-in resp [:headers "X-Total"])))
        (is (= "2" (get-in resp [:headers "X-Per-Page"])))))

    (testing "GET /api/users/search - unauthorized"
      (let [resp (app (mock/request :get "/api/users/search" {"q" "test"}))]
        (is (= 401 (:status resp)))))))
