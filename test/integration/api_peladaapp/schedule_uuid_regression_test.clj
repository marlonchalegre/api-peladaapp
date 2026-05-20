(ns api-peladaapp.schedule-uuid-regression-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest schedule-uuid-mapping-regression-test
  (let [app (-> th/*test-system* :app :app-handler)]

    ;; Register and login
    (app (-> (mock/request :post "/auth/register") (mock/json-body {"name" "Admin" "email" "admin@test.com" "password" "pass123"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {"email" "admin@test.com" "password" "pass123"})))
          token (:token (th/decode-body login))
          auth (th/auth-cookie token)

          ;; Create organization
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {"name" "UUID Club"})
                            auth))
          org-id (:id (th/decode-body org-resp))

          ;; Create pelada
          pelada-resp (app (-> (mock/request :post "/api/peladas")
                               (auth)
                               (mock/json-body {"organization_id" org-id
                                                "num_teams" 2
                                                "players_per_team" 5})))
          body (th/decode-body pelada-resp)
          pelada-id (:id body)]

      (testing "regression: full-details contains snake_case keys for teams and organization"
        (let [details-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) (auth)))
              details (th/decode-body details-resp)
              team (first (:teams details))]
          (is (= 200 (:status details-resp)))
          ;; Check pelada keys
          (is (some? (:organization_id (:pelada details))))
          (is (contains? (:pelada details) :has_schedule_plan))
          
          ;; Check team keys (regression: was returning pelada-id instead of pelada_id)
          (is (some? (:pelada_id team)))
          (is (not (contains? team :pelada-id)))
          (is (some? (:id team)))))

      (testing "regression: get-schedule-plan returns correct UUIDs (not nil)"
        (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) (auth))))
              team-ids (map :id (:teams details))
              t1 (first team-ids)
              t2 (second team-ids)
              custom-matches [{:home t1 :away t2}]
              _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/schedule"))
                         (auth)
                         (mock/json-body {"matches" custom-matches})))
              
              plan-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/schedule")) (auth)))
              plan (th/decode-body plan-resp)]
          (is (= 200 (:status plan-resp)))
          (is (= 1 (count plan)))
          ;; regression: was returning nil because of namespaced keys from DB
          (is (= t1 (:home (first plan))))
          (is (= t2 (:away (first plan)))))))))
