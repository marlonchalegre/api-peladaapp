(ns api-peladaapp.score-test
  (:require
   [api-peladaapp.logic.score :as logic.score]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc])
  (:import
   [java.time Duration Instant]))

(use-fixtures :each th/test-system-fixture)

(deftest get-normalized-scores-test
  (let [db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]

    ;; Setup Schema and Data
    (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES ('Org')"])
    (doseq [[i name email] [[1 "Ana" "ana@example.com"] [2 "Bob" "bob@example.com"] [3 "Cid" "cid@example.com"]]]
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password) VALUES (?, ?, ?, 'p')" i name email])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, organization_id, user_id) VALUES (?, 1, ?)" i i]))
    (let [closed-at (str (.minus (Instant/now) (Duration/ofHours 2)))]
      (jdbc/execute! ds ["INSERT INTO Peladas (id, organization_id, scheduled_at, status, closed_at) VALUES (1, 1, '2025-10-28', 'closed', ?)" closed-at]))
    (jdbc/execute! ds ["INSERT INTO Votes (pelada_id, voter_id, target_id, stars) VALUES (1, 2, 1, 5)"])
    (jdbc/execute! ds ["INSERT INTO Votes (pelada_id, voter_id, target_id, stars) VALUES (1, 3, 1, 3)"])
    (jdbc/execute! ds ["INSERT INTO Votes (pelada_id, voter_id, target_id, stars) VALUES (1, 1, 2, 4)"])

    (testing "Fetches grades for given player IDs"
      (let [player-ids [1 2 3]
            scores (logic.score/get-normalized-scores player-ids ds)]
        (is (= 5.0 (get scores 1))) ;; Default since no grade was set
        (is (= 5.0 (get scores 2))) ;; Default
        (is (= 5.0 (get scores 3))))) ;; Default

    (testing "Pulls directly from player grade column"
      (jdbc/execute! ds ["INSERT INTO Users (id, name, email, password) VALUES (4, 'Dani', 'dani@e.com', 'p')"])
      (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (id, organization_id, user_id, grade) VALUES (4, 1, 4, 7.5)"])
      (let [scores (logic.score/get-normalized-scores [4] ds)]
        (is (= 7.5 (get scores 4)))))))