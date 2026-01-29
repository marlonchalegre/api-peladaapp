(ns api-peladaapp.randomize-test
  (:require
   [api-peladaapp.logic.randomize :as logic.randomize]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
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

        (let [t1-rows (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 1"])
              t1-players (set (map :TeamPlayers/player_id t1-rows))
              t2-players (set (map :TeamPlayers/player_id (sql/query ds ["SELECT player_id FROM TeamPlayers WHERE team_id = 2"])))
              team-pairings (if (contains? t1-players 1) ;; If GK is in T1
                              [t1-players t2-players]
                              [t2-players t1-players])
              gk-team (first team-pairings)
              other-team (second team-pairings)]
          
          ;; Expected: GK (1) goes first to a team.
          ;; Def (4) goes to the other team (lowest score).
          ;; Str1 (3, score 8) goes to Def's team (score 7 < 10).
          ;; Str2 (2, score 5) goes to GK's team.
          
          ;; GK Team should have {1, 2}
          ;; Other Team should have {4, 3}
          (is (= #{1 2} gk-team))
          (is (= #{3 4} other-team)))))))