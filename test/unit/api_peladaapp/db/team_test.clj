(ns api-peladaapp.db.team-test
  (:require
   [api-peladaapp.db.team :as db.team]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [next.jdbc :as jdbc]))

(deftest test-crud-team
  (let [db "dummy-db"
        team-uuid (random-uuid)
        pelada-uuid (random-uuid)]
    (testing "insert-team"
      (with-redefs [jdbc/execute-one! (fn [_ query _]
                                        (is (str/includes? (first query) "INSERT INTO"))
                                        {:id team-uuid})]
        (is (= team-uuid (db.team/insert-team {:pelada-id pelada-uuid :name "Team A"} db)))))

    (testing "get-team"
      (with-redefs [jdbc/execute-one! (fn [_ query _]
                                        (is (str/includes? (first query) "SELECT"))
                                        {:id team-uuid :name "Team A" :pelada_id pelada-uuid})]
        (is (= {:id team-uuid :name "Team A" :pelada-id pelada-uuid} (db.team/get-team team-uuid db)))))

    (testing "update-team"
      (with-redefs [jdbc/execute-one! (fn [_ query _]
                                        (is (str/includes? (first query) "UPDATE"))
                                        {:next.jdbc/update-count 1})]
        (is (= 1 (db.team/update-team team-uuid {:name "Team B"} db)))))

    (testing "delete-team"
      (with-redefs [jdbc/execute-one! (fn [_ query _]
                                        (is (str/includes? (first query) "DELETE"))
                                        {:next.jdbc/update-count 1})]
        (is (= 1 (db.team/delete-team team-uuid db)))))

    (testing "list-pelada-teams"
      (with-redefs [jdbc/execute! (fn [_ query _]
                                    (is (str/includes? (first query) "SELECT"))
                                    [{:id team-uuid :name "Team A" :pelada_id pelada-uuid}])]
        (is (= [{:id team-uuid :name "Team A" :pelada-id pelada-uuid}] (db.team/list-pelada-teams pelada-uuid db)))))))

(deftest test-validations
  (let [db "dummy-db"
        team-uuid (random-uuid)
        player-uuid (random-uuid)]
    (testing "validate-player-belongs-to-pelada-org"
      (with-redefs [jdbc/execute-one! (fn [_ _ _] {:val 1})]
        (is (true? (db.team/validate-player-belongs-to-pelada-org team-uuid player-uuid db))))
      (with-redefs [jdbc/execute-one! (fn [_ _ _] nil)]
        (is (false? (db.team/validate-player-belongs-to-pelada-org team-uuid player-uuid db)))))

    (testing "validate-player-not-in-another-team-of-same-pelada"
      (with-redefs [jdbc/execute-one! (fn [_ _ _] {:val 1})]
        (is (false? (db.team/validate-player-not-in-another-team-of-same-pelada team-uuid player-uuid db))))
      (with-redefs [jdbc/execute-one! (fn [_ _ _] nil)]
        (is (true? (db.team/validate-player-not-in-another-team-of-same-pelada team-uuid player-uuid db)))))

    (testing "validate-team-not-full"
      (testing "when max_players is nil"
        (with-redefs [jdbc/execute-one! (fn [_ _ _] {:max_players nil :current_count 5})]
          (is (true? (db.team/validate-team-not-full team-uuid db)))))
      (testing "when current_count < max_players"
        (with-redefs [jdbc/execute-one! (fn [_ _ _] {:max_players 6 :current_count 5})]
          (is (true? (db.team/validate-team-not-full team-uuid db)))))
      (testing "when current_count >= max_players"
        (with-redefs [jdbc/execute-one! (fn [_ _ _] {:max_players 6 :current_count 6})]
          (is (false? (db.team/validate-team-not-full team-uuid db))))))))

(deftest test-add-player-to-team
  (let [db "dummy-db"
        team-uuid (random-uuid)
        player-uuid (random-uuid)]
    (testing "fails if player does not belong to organization"
      (with-redefs [db.team/validate-player-belongs-to-pelada-org (fn [_ _ _] false)]
        (is (thrown-with-msg? Exception #"Player does not belong"
                              (db.team/add-player-to-team team-uuid player-uuid db)))))

    (testing "fails if player is already in another team"
      (with-redefs [db.team/validate-player-belongs-to-pelada-org (fn [_ _ _] true)
                    db.team/validate-player-not-in-another-team-of-same-pelada (fn [_ _ _] false)]
        (is (thrown-with-msg? Exception #"Player is already in a team"
                              (db.team/add-player-to-team team-uuid player-uuid db)))))

    (testing "fails if team is full"
      (with-redefs [db.team/validate-player-belongs-to-pelada-org (fn [_ _ _] true)
                    db.team/validate-player-not-in-another-team-of-same-pelada (fn [_ _ _] true)
                    db.team/validate-team-not-full (fn [_ _] false)]
        (is (thrown-with-msg? Exception #"Team is full"
                              (db.team/add-player-to-team team-uuid player-uuid db)))))

    (testing "succeeds if validations pass"
      (with-redefs [db.team/validate-player-belongs-to-pelada-org (fn [_ _ _] true)
                    db.team/validate-player-not-in-another-team-of-same-pelada (fn [_ _ _] true)
                    db.team/validate-team-not-full (fn [_ _] true)
                    jdbc/execute-one! (fn [_ query _]
                                        (is (str/includes? (first query) "INSERT INTO"))
                                        {:next.jdbc/update-count 1})]
        (is (= 1 (db.team/add-player-to-team team-uuid player-uuid db)))))))

(deftest test-batch-and-removal
  (let [db "dummy-db"
        team-uuid (random-uuid)
        player-uuid (random-uuid)
        pelada-uuid (random-uuid)]
    (testing "add-team-players-batch!"
      (with-redefs [jdbc/execute! (fn [_ query _]
                                    (is (str/includes? (first query) "INSERT INTO"))
                                    [{:id 1}])]
        (is (= [{:id 1}] (db.team/add-team-players-batch! [{:team_id team-uuid :player_id player-uuid :is_goalkeeper false}] db)))))

    (testing "remove-player-from-team"
      (with-redefs [jdbc/execute-one! (fn [_ query _]
                                        (is (str/includes? (first query) "DELETE FROM"))
                                        {:next.jdbc/update-count 1})]
        (is (= 1 (db.team/remove-player-from-team team-uuid player-uuid db)))))

    (testing "clear-teams-players"
      (with-redefs [jdbc/execute-one! (fn [_ query _]
                                        (is (str/includes? (first query) "DELETE FROM"))
                                        {:next.jdbc/update-count 3})]
        (is (= 3 (db.team/clear-teams-players pelada-uuid db)))))))

(deftest test-lists-helpers
  (let [db "dummy-db"
        team-uuid (random-uuid)
        player-uuid (random-uuid)
        pelada-uuid (random-uuid)]
    (testing "list-team-players (boolean goalkeeper)"
      (with-redefs [jdbc/execute! (fn [_ query _]
                                    (is (str/includes? (first query) "SELECT"))
                                    [{:team_id team-uuid :player_id player-uuid :is_goalkeeper true}])]
        (is (= [{:team-id team-uuid :player-id player-uuid :is-goalkeeper true}]
               (db.team/list-team-players team-uuid db)))))

    (testing "list-team-players (numeric goalkeeper)"
      (with-redefs [jdbc/execute! (fn [_ _query _]
                                    [{:team_id team-uuid :player_id player-uuid :is_goalkeeper 0}])]
        (is (= [{:team-id team-uuid :player-id player-uuid :is-goalkeeper false}]
               (db.team/list-team-players team-uuid db)))))

    (testing "list-team-players-by-pelada"
      (with-redefs [jdbc/execute! (fn [_ query _]
                                    (is (str/includes? (first query) "SELECT"))
                                    [{:team_id team-uuid :player_id player-uuid :is_goalkeeper true :team_name "Team A" :pelada_id pelada-uuid}])]
        (is (= [{:team_id team-uuid :player_id player-uuid :is_goalkeeper true :team_name "Team A" :pelada_id pelada-uuid}]
               (db.team/list-team-players-by-pelada pelada-uuid db)))))

    (testing "list-team-players-with-names-by-pelada"
      (with-redefs [jdbc/execute! (fn [_ query _]
                                    (is (str/includes? (first query) "SELECT"))
                                    [{:team_id team-uuid :player_id player-uuid :is_goalkeeper true :team_name "Team A" :player_name "John" :position "Goalkeeper"}])]
        (is (= [{:team_id team-uuid :player_id player-uuid :is_goalkeeper true :team_name "Team A" :player_name "John" :position "Goalkeeper"}]
               (db.team/list-team-players-with-names-by-pelada pelada-uuid db)))))))

(deftest test-misc-queries
  (let [db "dummy-db"
        team-uuid (random-uuid)
        player-uuid (random-uuid)
        pelada-uuid (random-uuid)]
    (testing "did-player-participate-in-pelada?"
      (with-redefs [jdbc/execute-one! (fn [_ _ _] {:val 1})]
        (is (true? (db.team/did-player-participate-in-pelada? pelada-uuid player-uuid db))))
      (with-redefs [jdbc/execute-one! (fn [_ _ _] nil)]
        (is (false? (db.team/did-player-participate-in-pelada? pelada-uuid player-uuid db)))))

    (testing "is-goalkeeper?"
      (with-redefs [jdbc/execute-one! (fn [_ _ _] {:is_goalkeeper true})]
        (is (true? (db.team/is-goalkeeper? team-uuid player-uuid db))))
      (with-redefs [jdbc/execute-one! (fn [_ _ _] {:is_goalkeeper false})]
        (is (false? (db.team/is-goalkeeper? team-uuid player-uuid db)))))))
