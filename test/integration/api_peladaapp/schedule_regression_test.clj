(ns api-peladaapp.schedule-regression-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest schedule-regression-test
  (let [app (-> th/*test-system* :app :handler)]
    ;; Setup Admin and Org
    (app (-> (mock/request :post "/auth/register") (mock/json-body {"name" "Admin" "email" "admin@test.com" "password" "pass123"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {"email" "admin@test.com" "password" "pass123"})))
          token (:token (th/decode-body login))
          auth (th/auth-cookie token)

          org-resp (app (-> (mock/request :post "/api/organizations") (mock/json-body {"name" "Regression Club"}) auth))
          org-id (:id (th/decode-body org-resp))

          ;; Create Pelada 1
          p1-resp (app (-> (mock/request :post "/api/peladas") (auth) (mock/json-body {"organization_id" org-id "num_teams" 3})))
          p1-id (:id (th/decode-body p1-resp))

          ;; Create Pelada 2 (to get other team IDs)
          p2-resp (app (-> (mock/request :post "/api/peladas") (auth) (mock/json-body {"organization_id" org-id "num_teams" 2})))
          p2-id (:id (th/decode-body p2-resp))
          p2-details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" p2-id "/full-details")) (auth))))
          other-team-id (-> p2-details :teams first :id)]

      (testing "Prevent saving plan with invalid teams (Bug fix verification)"
        (let [save-resp (app (-> (mock/request :post (str "/api/peladas/" p1-id "/schedule"))
                                 (auth)
                                 (mock/json-body {"matches_per_team" 2
                                                  "matches" [{:home other-team-id :away other-team-id}]})))]
          (is (= 400 (:status save-resp)))
          (is (= "Invalid teams in schedule plan. Please refresh and try again." (:message (th/decode-body save-resp)))))))))
