(ns api-peladaapp.score-recalculation-test
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest score-recalculation-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)

        ;; Setup: Admin
        token-admin (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        auth-admin (th/auth-cookie token-admin)

        org-resp (app (-> (mock/request :post "/api/organizations") (mock/json-body {:name "Org"}) auth-admin))
        org-id (misc/as-uuid (:id (th/decode-body org-resp)))]

    ;; Register Player 1 and Player 2
    (th/register-and-login! app {:name "Player 1" :email "p1@test.com" :password "pass"})
    (th/register-and-login! app {:name "Player 2" :email "p2@test.com" :password "pass"})

    ;; Add players to organization
    (doseq [email ["p1@test.com" "p2@test.com"]]
      (let [uid (th/user-id-by-email ds email)]
        (db.player/insert-player {:organization-id org-id :user-id uid} ds)))

    (let [pelada-id (misc/as-uuid (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                                (mock/json-body {:organization_id org-id})
                                                                auth-admin)))))
          t1-id (misc/as-uuid (:id (th/decode-body (app (-> (mock/request :post "/api/teams")
                                                            (mock/json-body {:pelada_id pelada-id :name "Team A"})
                                                            auth-admin)))))
          t2-id (misc/as-uuid (:id (th/decode-body (app (-> (mock/request :post "/api/teams")
                                                            (mock/json-body {:pelada_id pelada-id :name "Team B"})
                                                            auth-admin)))))
          p-admin-id (th/player-id-by-user-id ds (th/user-id-by-email ds "admin@test.com") org-id)
          p1-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p1@test.com") org-id)
          p2-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p2@test.com") org-id)]

      ;; Assign Admin and Player 1 to Team A, Player 2 to Team B
      (doseq [pid [p-admin-id p1-id]]
        (exec! ds (-> (h/insert-into :TeamPlayers) (h/values [{:team_id t1-id :player_id pid}]))))
      (exec! ds (-> (h/insert-into :TeamPlayers) (h/values [{:team_id t2-id :player_id p2-id}])))

      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth-admin))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth-admin))

      ;; Get matches and players
      (let [dashboard-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
            dashboard (th/decode-body dashboard-resp)
            match-id (-> dashboard :matches first :id)
            home-team-id (parse-uuid (-> dashboard :matches first :home_team_id))
            away-team-id (parse-uuid (-> dashboard :matches first :away_team_id))

            ;; Determine which player is on which side based on the matches
            home-player-id (if (= home-team-id t1-id) p1-id p2-id)
            away-player-id (if (= away-team-id t1-id) p1-id p2-id)]

        (testing "Verify initial score is 0 - 0"
          (let [match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                          th/decode-body
                          :matches
                          first)]
            (is (= 0 (:home_score match)))
            (is (= 0 (:away_score match)))))

        (testing "Record goal for home player -> score becomes 1 - 0 and player stats update"
          (let [goal-resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                                   (mock/json-body {:player_id (str home-player-id) :event_type "goal"})
                                   auth-admin))
                goal-id (:id (th/decode-body goal-resp))
                match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                          th/decode-body
                          :matches
                          first)
                stats (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                          th/decode-body
                          :player_stats)
                home-player-stats (first (filter #(= (:player_id %) (str home-player-id)) stats))]
            (is (= 1 (:home_score match)))
            (is (= 0 (:away_score match)))
            (is (= 1 (:goals home-player-stats)))

            (testing "Record own-goal for home player -> score becomes 1 - 1 (since own_goal counts for away team)"
              (let [og-resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                                     (mock/json-body {:player_id (str home-player-id) :event_type "own_goal"})
                                     auth-admin))
                    og-id (:id (th/decode-body og-resp))
                    match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                              th/decode-body
                              :matches
                              first)
                    stats (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                              th/decode-body
                              :player_stats)
                    home-player-stats (first (filter #(= (:player_id %) (str home-player-id)) stats))]
                (is (= 1 (:home_score match)))
                (is (= 1 (:away_score match)))
                (is (= 1 (:own_goals home-player-stats)))

                (testing "Update own-goal scorer to away player -> score becomes 2 - 0 (own-goal by away player scores for home team) and stats update"
                  (app (-> (mock/request :put (str "/api/matches/" match-id "/events/" og-id))
                           (mock/json-body {:player_id (str away-player-id)})
                           auth-admin))
                  (let [match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                                  th/decode-body
                                  :matches
                                  first)
                        stats (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                                  th/decode-body
                                  :player_stats)
                        home-player-stats (first (filter #(= (:player_id %) (str home-player-id)) stats))
                        away-player-stats (first (filter #(= (:player_id %) (str away-player-id)) stats))]
                    (is (= 2 (:home_score match)))
                    (is (= 0 (:away_score match)))
                    (is (= 0 (:own_goals home-player-stats)))
                    (is (= 1 (:own_goals away-player-stats))))

                  (testing "Delete own-goal event -> score reverts to 1 - 0 and stats update"
                    (app (-> (mock/request :delete (str "/api/matches/" match-id "/events"))
                             (mock/json-body {:player_id (str away-player-id) :event_type "own_goal" :id (str og-id)})
                             auth-admin))
                    (let [match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                                    th/decode-body
                                    :matches
                                    first)
                          stats (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                                    th/decode-body
                                    :player_stats)
                          away-player-stats (first (filter #(= (:player_id %) (str away-player-id)) stats))]
                      (is (= 1 (:home_score match)))
                      (is (= 0 (:away_score match)))
                      (is (= 0 (:own_goals away-player-stats))))

                    (testing "Delete goal event -> score reverts to 0 - 0 and stats update"
                      (app (-> (mock/request :delete (str "/api/matches/" match-id "/events"))
                               (mock/json-body {:player_id (str home-player-id) :event_type "goal" :id (str goal-id)})
                               auth-admin))
                      (let [match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                                      th/decode-body
                                      :matches
                                      first)
                            stats (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                                      th/decode-body
                                      :player_stats)
                            home-player-stats (first (filter #(= (:player_id %) (str home-player-id)) stats))]
                        (is (= 0 (:home_score match)))
                        (is (= 0 (:away_score match)))
                        (is (= 0 (:goals home-player-stats)))))))))))))))
