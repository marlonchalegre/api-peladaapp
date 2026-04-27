(ns api-peladaapp.randomize-test
  (:require
   [api-peladaapp.logic.randomize :as logic.randomize]
   [api-peladaapp.test-helpers :as th]
   [clojure.set]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]))

(use-fixtures :each th/test-system-fixture)

(deftest randomize-teams-logic-test
  (let [db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; Setup Data
    (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES ('Org')"])
    (jdbc/execute! ds ["INSERT INTO Peladas (organization_id, scheduled_at) VALUES (1, '2023-01-01')"])
    (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (1, 'T1')"])
    (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (1, 'T2')"])

    ;; Create Players
    (dotimes [i 6]
      (jdbc/execute! ds ["INSERT INTO Users (name, email, password) VALUES (?, ?, 'pw')" (str "U" i) (str "u" i "@e.com")])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (user_id, organization_id) VALUES (?, 1)" (inc i)]))

    (testing "Distributes players to fill teams"
      (let [player-ids [1 2 3 4 5 6]
            pelada-id 1
            players-per-team 3
            org-player-ids (set (map :organizationplayers/id (sql/query ds ["SELECT id FROM OrganizationPlayers"])))]

        (logic.randomize/randomize-teams! pelada-id player-ids players-per-team ds)

        (let [t1-players (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 1"])
              t2-players (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 2"])]
          (is (= 3 (count t1-players)))
          (is (= 3 (count t2-players)))
          ;; Ensure all players are assigned
          (let [assigned-ids (set (map :teamplayers/player_id (concat t1-players t2-players)))]
            (is (= org-player-ids assigned-ids))))))

    (testing "Balances teams by position and score"
      (jdbc/execute! ds ["DELETE FROM TeamPlayers"])
      (jdbc/execute! ds ["DELETE FROM OrganizationPlayers"])
      (jdbc/execute! ds ["DELETE FROM Users"])
      (jdbc/execute! ds ["DELETE FROM Teams"])
      (jdbc/execute! ds ["INSERT INTO Teams (id, pelada_id, name) VALUES (1, 1, 'TA')"])
      (jdbc/execute! ds ["INSERT INTO Teams (id, pelada_id, name) VALUES (2, 1, 'TB')"])

      ;; Setup specific scenario
      ;; P1: GK, score 10
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (1, 'GK', 'gk@e.com', 'p', 'Goalkeeper')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (1, 1, 1, 10.0)"])
      ;; P4: Defender, score 7
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (4, 'Def', 'def@e.com', 'p', 'Defender')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (4, 4, 1, 7.0)"])
      ;; P3: Striker, score 8
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (3, 'Str1', 's1@e.com', 'p', 'Striker')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (3, 3, 1, 8.0)"])
      ;; P2: Striker, score 5
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (2, 'Str2', 's2@e.com', 'p', 'Striker')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (2, 2, 1, 5.0)"])

      (let [player-ids [1 2 3 4]
            pelada-id 1
            players-per-team 2]
        (logic.randomize/randomize-teams! pelada-id player-ids players-per-team ds)

        (let [t1-players (->> (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 1"])
                              (map :TeamPlayers/player_id) set)
              t2-players (->> (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 2"])
                              (map :TeamPlayers/player_id) set)]
          ;; Verify positions are split (one GK and one Def in different teams)
          (let [gk-team-id (if (contains? t1-players 1) 1 2)
                def-team-id (if (contains? t1-players 4) 1 2)]
            (is (not= gk-team-id def-team-id) "GK and Defender should be in different teams"))

        ;; Verify each team has one striker
          (let [t1-strikers (clojure.set/intersection t1-players #{2 3})
                t2-strikers (clojure.set/intersection t2-players #{2 3})]
            (is (= 1 (count t1-strikers)))
            (is (= 1 (count t2-strikers))))

        ;; Verify balance is reasonable (difference <= 6 for this small set)
          (let [t1-score (->> (jdbc/execute! ds ["SELECT op.grade FROM TeamPlayers tp JOIN OrganizationPlayers op ON tp.player_id = op.id WHERE tp.team_id = 1"]
                                             {:builder-fn rs/as-unqualified-lower-maps})
                              (map :grade) (reduce + 0))
                t2-score (->> (jdbc/execute! ds ["SELECT op.grade FROM TeamPlayers tp JOIN OrganizationPlayers op ON tp.player_id = op.id WHERE tp.team_id = 2"]
                                             {:builder-fn rs/as-unqualified-lower-maps})
                              (map :grade) (reduce + 0))]
            (is (<= (abs (- t1-score t2-score)) 6.0) (str "Score difference should be reasonable, got " t1-score " vs " t2-score))))))
    (testing "Respects player limit with many players"
      (jdbc/execute! ds ["DELETE FROM TeamPlayers"])
      (jdbc/execute! ds ["DELETE FROM OrganizationPlayers"])
      (jdbc/execute! ds ["DELETE FROM Users"])
      (jdbc/execute! ds ["DELETE FROM Teams"])
      ;; Create 4 teams
      (dotimes [i 4]
        (jdbc/execute! ds ["INSERT INTO Teams (id, pelada_id, name) VALUES (?, 1, ?)" (inc i) (str "T" i)]))

      ;; Create 20 players
      (dotimes [i 20]
        (let [id (inc i)]
          (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (?, ?, ?, 'p', 'Midfielder')" id (str "P" i) (str "p" i "@e.com")])
          (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (?, ?, 1, 5.0)" id id])))

      (let [player-ids (range 1 21)
            pelada-id 1
            players-per-team 6]
        (logic.randomize/randomize-teams! pelada-id player-ids players-per-team ds)

        (let [team-counts (map :c (sql/query ds ["SELECT count(*) as c FROM TeamPlayers GROUP BY team_id"]))]
          (is (every? #(<= % 6) team-counts))
          (is (= 20 (reduce + team-counts))))))

    (testing "Respects existing players in teams"
      (jdbc/execute! ds ["DELETE FROM TeamPlayers"])
      (jdbc/execute! ds ["DELETE FROM OrganizationPlayers"])
      (jdbc/execute! ds ["DELETE FROM Users"])
      (jdbc/execute! ds ["DELETE FROM Teams"])

      (jdbc/execute! ds ["INSERT INTO Teams (id, pelada_id, name) VALUES (1, 1, 'T1')"])
      (jdbc/execute! ds ["INSERT INTO Teams (id, pelada_id, name) VALUES (2, 1, 'T2')"])

      ;; Create 4 players
      (dotimes [i 4]
        (let [id (inc i)]
          (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (?, ?, ?, 'p', 'Midfielder')" id (str "P" i) (str "p" i "@e.com")])
          (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (?, ?, 1, 5.0)" id id])))

      ;; Pre-fill Team 1 with Player 1
      (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (1, 1)"])

      ;; Randomize ALL players [1, 2, 3, 4] to reshuffle
      (let [player-ids [1 2 3 4]
            pelada-id 1
            players-per-team 2]
        (logic.randomize/randomize-teams! pelada-id player-ids players-per-team ds)

        (let [t1-count (:c (first (sql/query ds ["SELECT count(*) as c FROM TeamPlayers WHERE team_id = 1"])))
              t2-count (:c (first (sql/query ds ["SELECT count(*) as c FROM TeamPlayers WHERE team_id = 2"])))]
          ;; Total 4 players. T1 had 1 but it was cleared and re-randomized.
          ;; Both teams should be full now.
          (is (= 2 t1-count))
          (is (= 2 t2-count)))))

    (testing "Spreads positions across teams (no clumping)"
      (jdbc/execute! ds ["DELETE FROM TeamPlayers"])
      (jdbc/execute! ds ["DELETE FROM OrganizationPlayers"])
      (jdbc/execute! ds ["DELETE FROM Users"])
      (jdbc/execute! ds ["DELETE FROM Teams"])

      (jdbc/execute! ds ["INSERT INTO Teams (id, pelada_id, name) VALUES (1, 1, 'T1')"])
      (jdbc/execute! ds ["INSERT INTO Teams (id, pelada_id, name) VALUES (2, 1, 'T2')"])

      ;; 2 Goalkeepers, 2 Defenders
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (1, 'GK1', 'gk1@e.com', 'p', 'Goalkeeper')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (1, 1, 1, 10.0)"])
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (2, 'GK2', 'gk2@e.com', 'p', 'Goalkeeper')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (2, 2, 1, 10.0)"])

      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (3, 'DF1', 'df1@e.com', 'p', 'Defender')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (3, 3, 1, 5.0)"])
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (4, 'DF2', 'df2@e.com', 'p', 'Defender')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (4, 4, 1, 5.0)"])

      (let [player-ids [1 2 3 4]
            pelada-id 1
            players-per-team 2]
        (logic.randomize/randomize-teams! pelada-id player-ids players-per-team ds)

        ;; Each team MUST have 1 GK and 1 DF.
        ;; If they clumped, one team would have 2 GKs.
        (let [t1-players (set (map :TeamPlayers/player_id (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 1"])))
              t2-players (set (map :TeamPlayers/player_id (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 2"])))]

          ;; Verify T1 has exactly one of the GKs
          (is (= 1 (count (clojure.set/intersection t1-players #{1 2}))))
          ;; Verify T2 has exactly one of the GKs
          (is (= 1 (count (clojure.set/intersection t2-players #{1 2})))))))

    (testing "Handles empty player list gracefully"
      (let [player-ids []
            pelada-id 1
            players-per-team 6]
        ;; Should not throw and do nothing
        (is (nil? (logic.randomize/randomize-teams! pelada-id player-ids players-per-team ds)))))

    (testing "Handles players with no position"
      (jdbc/execute! ds ["DELETE FROM TeamPlayers"])
      (jdbc/execute! ds ["DELETE FROM OrganizationPlayers"])
      (jdbc/execute! ds ["DELETE FROM Users"])
      (jdbc/execute! ds ["DELETE FROM Teams"])
      (jdbc/execute! ds ["INSERT INTO Teams (id, pelada_id, name) VALUES (1, 1, 'T1')"])

      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password, position) VALUES (1, 'NoPos', 'np@e.com', 'p', NULL)"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, user_id, organization_id, grade) VALUES (1, 1, 1, 5.0)"])

      (let [player-ids [1]
            pelada-id 1
            players-per-team 2]
        (logic.randomize/randomize-teams! pelada-id player-ids players-per-team ds)
        (let [t1-players (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 1"])]
          (is (= 1 (count t1-players))))))))