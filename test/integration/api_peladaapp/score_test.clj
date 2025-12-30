(ns api-peladaapp.score-test
  (:require [clojure.test :refer :all]
            [api-peladaapp.logic.score :as logic.score]
            [api-peladaapp.test-helpers :as th]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql])
  (:import [java.time Instant Duration]))

(deftest get-normalized-scores-test
  (let [{:keys [db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        db-fn (constantly ds)]

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

    (testing "Fetches scores for given player IDs"
      (let [player-ids [1 2]
            scores (logic.score/get-normalized-scores player-ids db-fn)]
        (is (= 4.0 (get scores 1)))
        (is (= 4.0 (get scores 2)))))))