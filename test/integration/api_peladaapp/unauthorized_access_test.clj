(ns api-peladaapp.unauthorized-access-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest unauthorized-access-test
  (let [app (-> th/*test-system* :app :app-handler)
        ;; Register admin and a regular user
        admin-token (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        user-token (th/register-and-login! app {:name "User" :email "user@test.com" :password "pass"})
        admin-auth (th/auth-cookie admin-token)
        user-auth (th/auth-cookie user-token)

    ;; Admin creates an organization
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Restricted Org"})
                          admin-auth))
        org-id (parse-uuid (:id (th/decode-body org-resp)))]

    (testing "Regular user cannot delete organization"
      (let [resp (app (-> (mock/request :delete (str "/api/organizations/" org-id))
                          user-auth))]
        (is (= 403 (:status resp)))))

    (testing "Regular user cannot create pelada in org they don't admin"
      (let [resp (app (-> (mock/request :post "/api/peladas")
                          (mock/json-body {:organization_id org-id :num_teams 2})
                          user-auth))]
        (is (= 403 (:status resp)))))

    (testing "Regular user cannot close pelada in org they don't admin"
      (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                 (mock/json-body {:organization_id org-id})
                                 admin-auth))
            pelada-id (parse-uuid (:id (th/decode-body pelada-resp)))
            resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close"))
                          user-auth))]
        (is (= 403 (:status resp)))))

    (testing "Regular user cannot finish match in org they don't admin"
      (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                 (mock/json-body {:organization_id org-id :num_teams 2})
                                 admin-auth))
            pelada-id (parse-uuid (:id (th/decode-body pelada-resp)))
            ;; Close attendance first
            _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance"))
                       admin-auth))
            ;; Begin pelada to create matches
            _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                       admin-auth))
            details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data"))
                                             admin-auth)))
            match-id (parse-uuid (-> details :Matches first :id))
            resp (app (-> (mock/request :put (str "/api/matches/" match-id "/score"))
                          (mock/json-body {:status "finished"})
                          user-auth))]

        (is (= 403 (:status resp)))))

    (testing "Guest (no token) cannot list organizations"

      (let [resp (app (-> (mock/request :get "/api/organizations")))]
        (is (= 401 (:status resp)))))))
