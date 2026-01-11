(ns integration.api-peladaapp.statistics-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest organization-statistics-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "User 1" :email "user1@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "user1@test.com")
        _ (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES ('Org Test')"])
        org-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id, grade) VALUES (?, ?, 5.0)" org-id user-id])
        player-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Peladas (organization_id, scheduled_at, status) VALUES (?, '2026-01-10 10:00:00', 'closed')" org-id])
        pelada-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (?, 'Team A')" pelada-id])
        team-a-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (?, 'Team B')" pelada-id])
        team-b-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Matches (pelada_id, home_team_id, away_team_id, sequence, status) VALUES (?, ?, ?, 1, 'finished')" pelada-id team-a-id team-b-id])
        match-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)]

    ;; Add Player to Lineup
    (jdbc/execute! ds ["INSERT INTO MatchLineups (match_id, team_id, player_id) VALUES (?, ?, ?)" match-id team-a-id player-id])

    ;; Add Events
    (jdbc/execute! ds ["INSERT INTO MatchEvents (match_id, player_id, event_type) VALUES (?, ?, 'goal')" match-id player-id])
    (jdbc/execute! ds ["INSERT INTO MatchEvents (match_id, player_id, event_type) VALUES (?, ?, 'goal')" match-id player-id])
    (jdbc/execute! ds ["INSERT INTO MatchEvents (match_id, player_id, event_type) VALUES (?, ?, 'assist')" match-id player-id])

    ;; Test the endpoint for specific year
    (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                            (mock/query-string {:year 2026})
                            ((th/auth-header token))))
          body (th/decode-body response)
          stat (first body)]

      (is (= 200 (:status response)))
      (is (vector? body))
      (is (= 1 (count body)))
      (is (= "User 1" (:player_name stat)))
      (is (= 1 (:peladas_played stat)))
      (is (= 2 (:goal stat)))
      (is (= 1 (:assist stat)))
      (is (= 0 (:own_goal stat))))))

(deftest legacy-statistics-fallback-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Legacy User" :email "legacy@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "legacy@test.com")
        _ (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES ('Legacy Org')"])
        org-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id, grade) VALUES (?, ?, 5.0)" org-id user-id])
        player-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)

        ;; Setup Legacy Pelada in 2025 (No MatchLineups)
        _ (jdbc/execute! ds ["INSERT INTO Peladas (organization_id, scheduled_at, status) VALUES (?, '2025-05-15 10:00:00', 'closed')" org-id])
        pelada-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (?, 'Legacy Team A')" pelada-id])
        team-a-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (?, 'Legacy Team B')" pelada-id])
        team-b-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)

        ;; Add Player to TeamPlayers (Simulating legacy data structure)
        _ (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" team-a-id player-id])

        _ (jdbc/execute! ds ["INSERT INTO Matches (pelada_id, home_team_id, away_team_id, sequence, status) VALUES (?, ?, ?, 1, 'finished')" pelada-id team-a-id team-b-id])
        match-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)]

    ;; NO MatchLineups insertion here!

    ;; Add Events (Player played via TeamPlayers relation)
    (jdbc/execute! ds ["INSERT INTO MatchEvents (match_id, player_id, event_type) VALUES (?, ?, 'goal')" match-id player-id])

    ;; Test the endpoint for 2025
    (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                            (mock/query-string {:year 2025})
                            ((th/auth-header token))))
          body (th/decode-body response)
          stat (first body)]

      (is (= 200 (:status response)))
      (is (vector? body))
      (is (= 1 (count body)))
      (is (= "Legacy User" (:player_name stat)))
      (is (= 1 (:peladas_played stat))) ;; Should be 1 derived from TeamPlayers
      (is (= 1 (:goal stat)))
      (is (= 0 (:assist stat))))))

(deftest zero-stats-player-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Zero Stats User" :email "zero@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "zero@test.com")
        _ (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES ('Zero Stats Org')"])
        org-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id, grade) VALUES (?, ?, 5.0)" org-id user-id])
        player-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Peladas (organization_id, scheduled_at, status) VALUES (?, '2026-06-01 10:00:00', 'closed')" org-id])
        pelada-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (?, 'Team Z')" pelada-id])
        team-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (?, 'Team Y')" pelada-id])
        opponent-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Matches (pelada_id, home_team_id, away_team_id, sequence, status) VALUES (?, ?, ?, 1, 'finished')" pelada-id team-id opponent-id])
        match-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)]

    ;; Player participated but NO events
    (jdbc/execute! ds ["INSERT INTO MatchLineups (match_id, team_id, player_id) VALUES (?, ?, ?)" match-id team-id player-id])

    (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                            (mock/query-string {:year 2026})
                            ((th/auth-header token))))
          body (th/decode-body response)
          stat (first body)]

      (is (= 200 (:status response)))
      (is (vector? body))
      (is (= 1 (count body)))
      (is (= "Zero Stats User" (:player_name stat)))
      (is (= 1 (:peladas_played stat)))
      (is (= 0 (:goal stat)))
      (is (= 0 (:assist stat)))
      (is (= 0 (:own_goal stat))))))

(deftest empty-year-statistics-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "User" :email "user@test.com" :password "pass123"})
        _ (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES ('Org')"])
        org-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)]

    (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                            (mock/query-string {:year 2030}) ;; Future year with no data
                            ((th/auth-header token))))
          body (th/decode-body response)]

      (is (= 200 (:status response)))
      (is (vector? body))
      (is (empty? body)))))

(deftest unauthorized-statistics-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        _ (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES ('Org')"])
        org-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)]

    (let [response (app (mock/request :get (str "/api/organizations/" org-id "/statistics")))]
      ;; Should return 401 Unauthorized because no token is provided
      (is (= 401 (:status response))))))