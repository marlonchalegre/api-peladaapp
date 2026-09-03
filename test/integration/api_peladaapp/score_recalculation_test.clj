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

(deftest substitution-score-preservation-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)

        token-admin (th/register-and-login! app {:name "Admin" :email "admin-sub@test.com" :password "pass"})
        auth-admin (th/auth-cookie token-admin)

        org-resp (app (-> (mock/request :post "/api/organizations") (mock/json-body {:name "Org"}) auth-admin))
        org-id (misc/as-uuid (:id (th/decode-body org-resp)))]

    ;; Register Player 1, Player 2 and Player 3 (substitute)
    (th/register-and-login! app {:name "Player 1" :email "p1-sub@test.com" :password "pass"})
    (th/register-and-login! app {:name "Player 2" :email "p2-sub@test.com" :password "pass"})
    (th/register-and-login! app {:name "Player 3" :email "p3-sub@test.com" :password "pass"})

    (doseq [email ["p1-sub@test.com" "p2-sub@test.com" "p3-sub@test.com"]]
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
          p1-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p1-sub@test.com") org-id)
          p2-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p2-sub@test.com") org-id)
          p3-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p3-sub@test.com") org-id)]

      ;; Assign Player 1 to Team A, Player 2 to Team B. Player 3 is on the bench (unassigned)
      (exec! ds (-> (h/insert-into :TeamPlayers) (h/values [{:team_id t1-id :player_id p1-id}])))
      (exec! ds (-> (h/insert-into :TeamPlayers) (h/values [{:team_id t2-id :player_id p2-id}])))

      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth-admin))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth-admin))

      (let [dashboard-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
            dashboard (th/decode-body dashboard-resp)
            match-id (-> dashboard :matches first :id)
            home-team-id (misc/as-uuid (-> dashboard :matches first :home_team_id))]

        (testing "Initial score is 0 - 0"
          (let [match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                          th/decode-body :matches first)]
            (is (= 0 (:home_score match)))
            (is (= 0 (:away_score match)))))

        (testing "Substitute Player 1 with Player 3 in Team A"
          (let [sub-resp (app (-> (mock/request :post (str "/api/matches/" match-id "/lineups/replace"))
                                  (mock/json-body {:team_id (str home-team-id)
                                                   :out_player_id (str p1-id)
                                                   :in_player_id (str p3-id)})
                                  auth-admin))]
            (is (= 200 (:status sub-resp)))))

        (testing "Player 3 scores a goal -> score becomes 1 - 0 and Player 3 stats update"
          (let [goal-resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                                   (mock/json-body {:player_id (str p3-id) :event_type "goal"})
                                   auth-admin))
                match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                          th/decode-body :matches first)
                stats (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                          th/decode-body :player_stats)
                p3-stats (first (filter #(= (:player_id %) (str p3-id)) stats))]
            (is (= 200 (:status goal-resp)))
            (is (= 1 (:home_score match)))
            (is (= 0 (:away_score match)))
            (is (= 1 (:goals p3-stats)))))

        (testing "Substitute Player 3 back with Player 1 -> score MUST remain 1 - 0 and Player 3 keeps 1 goal"
          (let [sub-resp (app (-> (mock/request :post (str "/api/matches/" match-id "/lineups/replace"))
                                  (mock/json-body {:team_id (str home-team-id)
                                                   :out_player_id (str p3-id)
                                                   :in_player_id (str p1-id)})
                                  auth-admin))
                match (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                          th/decode-body :matches first)
                stats (-> (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth-admin))
                          th/decode-body :player_stats)
                p3-stats (first (filter #(= (:player_id %) (str p3-id)) stats))]
            (is (= 200 (:status sub-resp)))
            (is (= 1 (:home_score match)))
            (is (= 0 (:away_score match)))
            (is (= 1 (:goals p3-stats)))))))))

