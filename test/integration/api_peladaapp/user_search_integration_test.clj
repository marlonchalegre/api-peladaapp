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
        admin-token (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "admin123"})]

    ;; Seed data
    (db.user/insert-user {:name "Cristiano Ronaldo" :email "cr7@test.com" :password "pass"} ds)
    (db.user/insert-user {:name "Lionel Messi" :email "leo@test.com" :password "pass"} ds)
    (db.user/insert-user {:name "Neymar Jr" :email "ney@gmail.com" :password "pass"} ds)

    (testing "GET /api/users/search - search by name"
      (let [resp (app (-> (mock/request :get "/api/users/search")
                          ((th/auth-header admin-token))
                          (assoc :query-params {"q" "Messi"})))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1 (count body)))
        (is (= "Lionel Messi" (:name (first body))))))

    (testing "GET /api/users/search - search by email domain"
      (let [resp (app (-> (mock/request :get "/api/users/search")
                          ((th/auth-header admin-token))
                          (assoc :query-params {"q" "@test.com"})))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        ;; Admin + Cristiano + Lionel
        (is (= 3 (count body)))))

    (testing "GET /api/users/search - pagination headers"
      (let [resp (app (-> (mock/request :get "/api/users/search")
                          ((th/auth-header admin-token))
                          (assoc :query-params {"q" "" "per_page" "2"})))]
        (is (= 200 (:status resp)))
        ;; Total should be 4 (3 seeded + 1 admin)
        (is (= "4" (get-in resp [:headers "X-Total"])))
        (is (= "2" (get-in resp [:headers "X-Per-Page"])))))

    (testing "GET /api/users/search - unauthorized"
      (let [resp (app (mock/request :get "/api/users/search" {"q" "test"}))]
        (is (= 401 (:status resp)))))))
