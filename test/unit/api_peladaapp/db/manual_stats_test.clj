(ns api-peladaapp.db.manual-stats-test
  (:require
   [api-peladaapp.db.manual-stats :as db.ms]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [next.jdbc :as jdbc]))

(deftest test-upsert-manual-stats
  (let [db "dummy-db"
        org-uuid (random-uuid)
        player-uuid (random-uuid)]
    (testing "upsert manual stats with all values"
      (with-redefs [jdbc/execute-one! (fn [_ query _]
                                        (is (str/includes? (first query) "INSERT INTO"))
                                        (is (some #{org-uuid} query))
                                        {:next.jdbc/update-count 1})]
        (is (= 1 (db.ms/upsert-manual-stats {:organization-id org-uuid
                                             :player-id player-uuid
                                             :year 2023
                                             :goals 2
                                             :assists 3
                                             :own-goals 0}
                                            db)))))

    (testing "upsert manual stats with nil/optional values"
      (with-redefs [jdbc/execute-one! (fn [_ _query _]
                                        {:next.jdbc/update-count 1})]
        (is (= 1 (db.ms/upsert-manual-stats {:organization-id org-uuid
                                             :player-id player-uuid
                                             :year 2023}
                                            db)))))))

(deftest test-delete-manual-stats
  (let [db "dummy-db"
        org-uuid (random-uuid)
        player-uuid (random-uuid)]
    (with-redefs [jdbc/execute-one! (fn [_ query _]
                                      (is (str/includes? (first query) "DELETE FROM"))
                                      (is (some #{org-uuid} query))
                                      {:next.jdbc/update-count 1})]
      (is (= 1 (db.ms/delete-manual-stats org-uuid player-uuid 2023 db))))))

(deftest test-list-manual-stats-by-org-and-year
  (let [db "dummy-db"
        org-uuid (random-uuid)
        player-uuid (random-uuid)
        mock-row {:organization_id org-uuid :player_id player-uuid :year 2023 :goals 5 :assists 2 :own_goals 0}]
    (with-redefs [jdbc/execute! (fn [_ query _]
                                  (is (str/includes? (first query) "SELECT"))
                                  (is (some #{org-uuid} query))
                                  [mock-row])]
      (is (= [{:organization-id org-uuid :player-id player-uuid :year 2023 :goals 5 :assists 2 :own-goals 0}]
             (db.ms/list-manual-stats-by-org-and-year org-uuid 2023 db))))))

