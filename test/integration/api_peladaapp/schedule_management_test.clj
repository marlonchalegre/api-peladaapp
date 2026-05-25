(ns api-peladaapp.schedule-management-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest schedule-management-flow-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]

    ;; Register and login
    (app (-> (mock/request :post "/auth/register") (mock/json-body {"name" "Admin" "email" "admin@test.com" "password" "pass123"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {"email" "admin@test.com" "password" "pass123"})))
          token (:token (th/decode-body login))
          auth (th/auth-cookie token)

          _ (th/grant-org-creation! ds "admin@test.com")

          ;; Create organization
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {"name" "Schedule Club"})
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

      (testing "get-schedule-preview with < 2 teams"
        ;; Pelada created with num_teams 2 already has 2 teams created automatically by controller
        ;; Let's create a new one with 0 teams to test the edge case
        (let [p2-resp (app (-> (mock/request :post "/api/peladas")
                               (auth)
                               (mock/json-body {"organization_id" org-id
                                                "num_teams" 0})))
              p2-id (:id (th/decode-body p2-resp))
              preview (app (-> (mock/request :get (str "/api/peladas/" p2-id "/schedule/preview"))
                               (auth)))]
          (is (= 200 (:status preview)))
          (is (empty? (:matches (th/decode-body preview))))))

      (testing "get-schedule-preview with 2 teams"
        (let [preview-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/schedule/preview") {"matches_per_team" "2"})
                                    (auth)))
              preview (th/decode-body preview-resp)]
          (is (= 200 (:status preview-resp)))
          (is (= 2 (count (:matches preview))))
          (is (false? (:is_from_format preview)))))

      (testing "save-schedule-plan"
        (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) (auth))))
              team-ids (map :id (:teams details))
              t1 (first team-ids)
              t2 (second team-ids)
              custom-matches [{:home t1 :away t2}
                              {:home t2 :away t1}
                              {:home t1 :away t2}]
              save-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/schedule"))
                                 (auth)
                                 (mock/json-body {"matches_per_team" 3
                                                  "matches" custom-matches})))]
          (is (= 200 (:status save-resp)))

          (testing "verify saved plan"
            (let [plan-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/schedule"))
                                     (auth)))
                  plan (th/decode-body plan-resp)]
              (is (= 200 (:status plan-resp)))
              (is (= 3 (count plan)))
              (is (= t1 (:home (first plan))))))

          (testing "verify has_schedule_plan flag in full-details"
            (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) (auth))))]
              (is (true? (:has_schedule_plan (:pelada details))))))

          (testing "begin pelada uses custom schedule"
            ;; Close attendance first
            (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth))

            (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                                      (auth)
                                      (mock/json-body {"matches_per_team" 3})))]
              (is (= 200 (:status begin-resp)))
              (is (= 3 (:matches_created (th/decode-body begin-resp))))

              (let [dashboard (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) (auth))))
                    matches (:matches dashboard)]
                (is (= 3 (count matches)))
                (is (= t1 (:home_team_id (first matches))))
                (is (= t2 (:away_team_id (first matches))))))))))))
