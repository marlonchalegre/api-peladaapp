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

    (testing "Does not exceed players per team"
      ;; Additional tests could go here
      )))