(ns api-peladaapp.peladas-pagination-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest peladas-pagination-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        auth (th/auth-header token)

        ;; Create organization
        org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Test Org"})
                            auth))
        org-id (:id (th/decode-body org-resp))]

      ;; Create 25 peladas directly in DB to be faster
      (dotimes [i 25]
        (sql/insert! ds :Peladas {:organization_id org-id :scheduled_at (str "2025-01-" (inc i)) :num_teams 2}))

      ;; Test first page (default 20 items)
      (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/peladas"))
                          auth))
            body (th/decode-body resp)
            headers (:headers resp)]
        (is (= 200 (:status resp)))
        (is (= 20 (count body)))
        (is (= "25" (get headers "X-Total")))
        (is (= "2" (get headers "X-Total-Pages")))
        (is (= "1" (get headers "X-Page"))))

      ;; Test second page
      (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/peladas?page=2&per_page=20"))
                          auth))
            body (th/decode-body resp)
            headers (:headers resp)]
        (is (= 200 (:status resp)))
        (is (= 5 (count body)))
        (is (= "2" (get headers "X-Page"))))))