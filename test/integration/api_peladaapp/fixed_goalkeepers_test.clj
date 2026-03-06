(ns api-peladaapp.fixed-goalkeepers-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest global-fixed-goalkeepers-flow-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]

    ;; Register and login
    (app (-> (mock/request :post "/auth/register") (mock/json-body {"name" "Admin" "email" "admin@test.com" "password" "pass123"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {"email" "admin@test.com" "password" "pass123"})))
          token (:token (th/decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))
          user-id (th/user-id-by-email ds "admin@test.com")

          ;; Create organization
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {"name" "Global GK Club"})
                            auth))
          org-id (:id (th/decode-body org-resp))]

      (is (= 201 (:status org-resp)))

      (testing "create pelada with fixed goalkeepers"
        (let [resp (app (-> (mock/request :post "/api/peladas")
                            (auth)
                            (mock/json-body {"organization_id" org-id
                                             "num_teams" 2
                                             "players_per_team" 5
                                             "fixed_goalkeepers" true})))
              body (th/decode-body resp)
              pelada-id (:id body)
              player-id 1]
          (is (= 201 (:status resp)))
          (is (= true (:fixed_goalkeepers body)))

          (testing "setting and reassigning goalkeepers"
            (let [set-resp (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                                    (auth)
                                    (mock/json-body {"home_fixed_goalkeeper_id" player-id})))]
              (is (= 200 (:status set-resp)))
              (is (= player-id (:home_fixed_goalkeeper_id (th/decode-body set-resp)))))

            ;; Swap to away
            (let [swap-resp (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                                     (auth)
                                     (mock/json-body {"home_fixed_goalkeeper_id" nil
                                                      "away_fixed_goalkeeper_id" player-id})))]
              (is (= 200 (:status swap-resp)))
              (let [b (th/decode-body swap-resp)]
                (is (nil? (:home_fixed_goalkeeper_id b)))
                (is (= player-id (:away_fixed_goalkeeper_id b))))))

          (testing "randomization ignores global fixed goalkeeper"
            ;; Put it back to home for test
            (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                     (auth)
                     (mock/json-body {"home_fixed_goalkeeper_id" player-id "away_fixed_goalkeeper_id" nil})))

            ;; Confirm attendance and close list
            (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
                     (mock/json-body {"status" "confirmed"})
                     auth))
            (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance"))
                     auth))

            (let [rand-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/teams/randomize"))
                                     (auth)
                                     (mock/json-body {"player_ids" [player-id] "players_per_team" 5})))]
              (is (= 200 (:status rand-resp)))

              ;; Verify player is NOT in any team
              (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) (auth))))]
                (is (empty? (mapcat :players (:teams details)))))))

          (testing "match lineups injection"
            (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                     (mock/json-body {"matches_per_team" 1})
                     auth))

            (let [dashboard (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) (auth))))
                  match (first (:matches dashboard))
                  mid (keyword (str (:id match)))
                  lineups (get (:match_lineups_map dashboard) mid)
                  all-lineup-players (mapcat val lineups)]
              (is (some (fn [p] (and (= player-id (:player_id p)) (= 1 (:is_goalkeeper p)))) all-lineup-players))))))

      (testing "behavior when feature is disabled"
        (let [resp (app (-> (mock/request :post "/api/peladas")
                            (auth)
                            (mock/json-body {"organization_id" org-id
                                             "num_teams" 2
                                             "players_per_team" 5
                                             "fixed_goalkeepers" false})))
              pelada-id (:id (th/decode-body resp))]

          ;; Randomization should NOT ignore the player if they are passed in player_ids
          (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth))
          (let [rand-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/teams/randomize"))
                                   (auth)
                                   (mock/json-body {"player_ids" [1] "players_per_team" 5})))]
            (is (= 200 (:status rand-resp)))
            (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) (auth))))]
              (is (= 1 (count (mapcat :players (:teams details))))))))))))
