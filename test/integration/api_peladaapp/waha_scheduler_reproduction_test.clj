(ns api-peladaapp.waha-scheduler-reproduction-test
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]))

(use-fixtures :once th/test-system-fixture)

(defn- iso-date [instant]
  (str instant))

(deftest test-list-peladas-for-vote-notification-reproduction
  (let [db-file (:db-file th/*test-system*)
        db (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (testing "REPRODUCTION: Should return peladas for 30m, 12h and 23h reminders even with ISO-8601 format"
      (jdbc/execute! db ["INSERT INTO Organizations (name) VALUES ('Org 1')"])
      (let [now (java.time.Instant/now)
            date-31m (iso-date (.minus now (java.time.Duration/ofMinutes 31)))
            date-12-5h (iso-date (.minus now (java.time.Duration/ofMinutes (+ (* 12 60) 30)))) ;; 12.5h ago
            org-id 1]

        ;; Pelada 1: Closed 12.5h ago, 12h reminder NOT sent -> SHOULD be returned as :12h
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = 1" date-12-5h])

        ;; Pelada 2: Closed 31m ago, 30m reminder NOT sent -> SHOULD be returned as :30m
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = 2" date-31m])

        (let [results (db.pelada/list-peladas-for-vote-reminders db)]
          ;; These assertions are expected to FAIL before the fix
          (is (some #(= :12h (:type %)) results) "Should find 12h reminder")
          (is (some #(= :30m (:type %)) results) "Should find 30m reminder"))))))
