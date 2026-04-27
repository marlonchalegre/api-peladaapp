(ns api-peladaapp.dashboard-data-pii-test
  (:require
   [api-peladaapp.server :as server]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest get-dashboard-data-pii-test
  (let [db-raw (-> th/*test-system* :database :database)
        db (if (fn? db-raw) (db-raw) db-raw)
        app (fn [req] (server/app (assoc req :database db)))

        token1 (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass" :phone "1234567890"})
        auth1 (th/auth-cookie token1)

        ;; create a user that is not in the pelada or org, with PII
        _ (th/register-and-login! app {:name "Random" :email "random@test.com" :password "pass2" :phone "0987654321"})

        ;; Setup: Org -> Pelada by Admin
        org-resp (app (-> (mock/request :post "/api/organizations") (mock/json-body {:name "Org"}) auth1))
        org-id (:id (th/decode-body org-resp))
        pelada-resp (app (-> (mock/request :post "/api/peladas") (mock/json-body {:organization_id org-id}) auth1))
        pelada-id (:id (th/decode-body pelada-resp))]

    (testing "dashboard-data excludes PII and unreferenced users"
      (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth1))
            body (th/decode-body resp)
            users (:users body)]
        (is (= 200 (:status resp)))
        ;; Only 1 user should be referenced (Admin)
        (is (= 1 (count users)))
        (let [user (first users)]
          (is (= "Admin" (:name user)))
          (is (nil? (:email user)))
          (is (nil? (:phone user)))
          (is (nil? (:password user))))))))
