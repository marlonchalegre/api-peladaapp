(ns api-peladaapp.unauthorized-access-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest unauthorized-access-test
  (let [app (-> th/*test-system* :app :handler)
        ;; Register admin and a regular user
        admin-token (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        user-token (th/register-and-login! app {:name "User" :email "user@test.com" :password "pass"})
        admin-auth (th/auth-header admin-token)
        user-auth (th/auth-header user-token)

    ;; Admin creates an organization
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Restricted Org"})
                          admin-auth))
        org-id (:id (th/decode-body org-resp))]

    (testing "Regular user cannot delete organization"
      (let [resp (app (-> (mock/request :delete (str "/api/organizations/" org-id))
                          user-auth))]
        (is (= 403 (:status resp)))))

    (testing "Regular user cannot create pelada in org they don't admin"
      (let [resp (app (-> (mock/request :post "/api/peladas")
                          (mock/json-body {:organization_id org-id :num_teams 2})
                          user-auth))]
        (is (= 403 (:status resp)))))

    (testing "Guest (no token) cannot list organizations"
      (let [resp (app (-> (mock/request :get "/api/organizations")))]
        (is (= 401 (:status resp)))))))
