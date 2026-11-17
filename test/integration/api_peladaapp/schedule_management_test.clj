(ns api-peladaapp.schedule-management-test
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.helpers.misc :as misc]
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
                (is (= t2 (:away_team_id (first matches))))))))

        (testing "championship_final automatic progression and winner_stays_on progression"
          (let [champ-resp (app (-> (mock/request :post "/api/peladas")
                                    (auth)
                                    (mock/json-body {"organization_id" org-id
                                                     "num_teams" 3
                                                     "players_per_team" 5
                                                     "format" "championship_final"
                                                     "format_config" {"matches_per_team" 2
                                                                      "knockout_tiebreaker" "best_campaign"}})))
                champ-id (:id (th/decode-body champ-resp))]
            (is (= "championship_final" (:format (th/decode-body champ-resp))))
          ;; Close attendance and start pelada
            (app (-> (mock/request :post (str "/api/peladas/" champ-id "/close-attendance")) auth))
            (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" champ-id "/begin"))
                                      (auth)
                                      (mock/json-body {"matches_per_team" 2
                                                       "format" "championship_final"})))]
              (is (= 200 (:status begin-resp)))
            ;; 3 teams * 2 matches / 2 = 3 group matches
              (is (= 3 (:matches_created (th/decode-body begin-resp))))
              (let [dash1 (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" champ-id "/dashboard-data")) (auth))))
                    group-matches (:matches dash1)]
                (is (every? #(= "group" (:stage %)) group-matches))
              ;; Finish all 3 group matches
                (doseq [[idx m] (map-indexed vector group-matches)]
                  (app (-> (mock/request :put (str "/api/matches/" (:id m) "/score"))
                           (auth)
                           (mock/json-body {"home_score" (if (zero? idx) 3 1)
                                            "away_score" 0
                                            "status" "finished"}))))
              ;; Verify the Final match was automatically created!
                (let [dash2 (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" champ-id "/dashboard-data")) (auth))))
                      all-matches (:matches dash2)
                      final-match (last all-matches)]
                  (is (= 4 (count all-matches)))
                  (is (= "final" (:stage final-match)))
                  (is (= "scheduled" (:status final-match))))))))

        (testing "arrival_order draw and winner_stays_on dynamic lineup completion"
          (let [;; Add 6 players to org and confirm them in a new 2-team, 2-players-per-team pelada
                p-ids (vec (for [i (range 1 7)]
                             (let [email (str "p" i "@test.com")]
                               (app (-> (mock/request :post "/auth/register")
                                        (mock/json-body {"name" (str "Player " i) "email" email "password" "pass123"})))
                               (let [u-resp (app (-> (mock/request :post "/auth/login")
                                                     (mock/json-body {"email" email "password" "pass123"})))
                                     uid (misc/as-uuid (get-in (th/decode-body u-resp) [:user :id]))]
                                 (str (db.player/insert-player {:user-id uid
                                                                :organization-id (misc/as-uuid org-id)
                                                                :grade (+ 5.0 i)}
                                                               ds))))))
                wso-resp (app (-> (mock/request :post "/api/peladas")
                                  (auth)
                                  (mock/json-body {"organization_id" org-id
                                                   "num_teams" 2
                                                   "players_per_team" 2
                                                   "format" "winner_stays_on"
                                                   "format_config" {"rotation_mode" "player_queue"
                                                                    "complete_team_rule" "least_played"}})))
                wso-id (:id (th/decode-body wso-resp))]
            (app (-> (mock/request :post (str "/api/peladas/" wso-id "/attendance/batch"))
                     (auth)
                     (mock/json-body {"player_ids" p-ids "status" "confirmed"})))
            (let [rev-ids (vec (reverse p-ids))
                  ao-resp (app (-> (mock/request :post (str "/api/peladas/" wso-id "/attendance/arrival-order"))
                                   (auth)
                                   (mock/json-body {"player_ids" rev-ids})))]
              (is (= 200 (:status ao-resp)))
              (let [draw-resp (app (-> (mock/request :post (str "/api/peladas/" wso-id "/teams/randomize"))
                                       (auth)
                                       (mock/json-body {"algorithm" "arrival_fifo"
                                                        "players_per_team" 2
                                                        "num_teams" 2})))]
                (is (= 200 (:status draw-resp)))
                (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" wso-id "/full-details")) (auth))))
                      t1-players (mapv :id (:players (first (:teams details))))
                      t2-players (mapv :id (:players (second (:teams details))))]
                  ;; First 2 of rev-ids -> team 1, next 2 -> team 2, last 2 -> bench
                  (is (= (set (subvec rev-ids 0 2)) (set t1-players)))
                  (is (= (set (subvec rev-ids 2 4)) (set t2-players)))
                  (app (-> (mock/request :post (str "/api/peladas/" wso-id "/close-attendance")) auth))
                  (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" wso-id "/begin"))
                                            (auth)
                                            (mock/json-body {"format" "winner_stays_on"})))]
                    (is (= 200 (:status begin-resp)))
                    (is (= 1 (:matches_created (th/decode-body begin-resp))))
                    (let [dash1 (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" wso-id "/dashboard-data")) (auth))))
                          m1 (first (:matches dash1))]
                    ;; Finish match 1 with home winning 2-0 so the 2 bench players enter as challenger
                      (app (-> (mock/request :put (str "/api/matches/" (:id m1) "/score"))
                               (auth)
                               (mock/json-body {"home_score" 2 "away_score" 0 "status" "finished"})))
                      (let [dash2 (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" wso-id "/dashboard-data")) (auth))))
                            m2 (second (:matches dash2))
                            m2-lineups (get-in dash2 [:match_lineups_map (keyword (str (:id m2)))])
                            away-lineup-pids (mapv :player_id (get m2-lineups (keyword (str (:away_team_id m2)))))]
                        (is (= 2 (count (:matches dash2))))
                        (is (= (set (subvec rev-ids 4 6)) (set away-lineup-pids)))))))

                (testing "arrival_first_balanced draw and captain draft replace-all-teams-players"
                  (let [p2-resp (app (-> (mock/request :post "/api/peladas")
                                         (auth)
                                         (mock/json-body {"organization_id" org-id
                                                          "num_teams" 3
                                                          "players_per_team" 2})))
                        p2-id (:id (th/decode-body p2-resp))
                        batch-resp (app (-> (mock/request :post (str "/api/peladas/" p2-id "/attendance/batch"))
                                            (auth)
                                            (mock/json-body {"player_ids" p-ids "status" "confirmed"})))]
                    (is (= 200 (:status batch-resp)))
                    (let [draw-bal (app (-> (mock/request :post (str "/api/peladas/" p2-id "/teams/randomize"))
                                            (auth)
                                            (mock/json-body {"algorithm" "arrival_first_balanced"
                                                             "players_per_team" 2
                                                             "num_teams" 3})))]
                      (is (= 200 (:status draw-bal))))
                    (let [details-before (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" p2-id "/full-details")) (auth))))
                          team-ids (mapv :id (:teams details-before))
                          _ (is (= [2 2 2] (mapv #(count (:players %)) (:teams details-before))))
                          draft-payload {(str (nth team-ids 0)) [(nth p-ids 0) (nth p-ids 3)]
                                         (str (nth team-ids 1)) [(nth p-ids 1) (nth p-ids 4)]
                                         (str (nth team-ids 2)) [(nth p-ids 2) (nth p-ids 5)]}
                          put-resp (app (-> (mock/request :put (str "/api/peladas/" p2-id "/teams/players"))
                                            (auth)
                                            (mock/json-body {"teams" draft-payload})))
                          details-after (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" p2-id "/full-details")) (auth))))
                          team-player-sets (into {} (map (fn [t] [(:id t) (set (map :id (:players t)))]) (:teams details-after)))]
                      (is (= 200 (:status put-resp)))
                      (is (= 6 (:assigned (th/decode-body put-resp))))
                      (is (= #{(nth p-ids 0) (nth p-ids 3)} (get team-player-sets (nth team-ids 0))))
                      (is (= #{(nth p-ids 1) (nth p-ids 4)} (get team-player-sets (nth team-ids 1))))
                      (is (= #{(nth p-ids 2) (nth p-ids 5)} (get team-player-sets (nth team-ids 2)))))))))))))))
