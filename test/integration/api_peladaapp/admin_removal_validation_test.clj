(ns api-peladaapp.admin-removal-validation-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest test-prevent-removing-last-admin
  (testing "Should prevent removing the last admin of an organization"
    (let [app (-> th/*test-system* :app :app-handler)
          ds (api-peladaapp.test-helpers/get-test-datasource)

          ;; Register and login
          token (th/register-and-login! app {:name "Admin User" :email "admin@test.com" :password "pass123"})
          auth (th/auth-cookie token)
          user-id (th/user-id-by-email ds "admin@test.com")

;; Create organization
          create-org-resp (app (-> (mock/request :post "/api/organizations")
                                   (mock/json-body {:name "Test Org"})
                                   auth))
          org-id (:id (th/decode-body create-org-resp))]

      ;; Verify we have 1 admin
      (let [list-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/admins")) auth))]
        (is (= 200 (:status list-resp)))
        (is (= 1 (count (th/decode-body list-resp)))))

      ;; Try to remove the only admin - should fail
      (let [remove-resp (app (-> (mock/request :delete (str "/api/organizations/" org-id "/admins/" user-id)) auth))]
        (is (= 400 (:status remove-resp)) "Should return 400 Bad Request when removing last admin"))

      ;; Verify admin is still there
      (let [list-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/admins")) auth))]
        (is (= 200 (:status list-resp)))
        (is (= 1 (count (th/decode-body list-resp))))))))
