(ns api-peladaapp.waha-scheduler-test
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]))

(use-fixtures :once th/test-system-fixture)

(defn- sqlite-date [instant]
  (let [formatter (-> (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")
                      (.withZone (java.time.ZoneId/of "UTC")))]
    (.format formatter instant)))

(deftest test-list-peladas-for-vote-notification
  (let [db-file (:db-file th/*test-system*)
        db (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (testing "Should return peladas closed more than 24h ago with message not sent"
      (jdbc/execute! db ["INSERT INTO Organizations (name) VALUES ('Org 1')"])
      (let [org-id 1
            now (java.time.Instant/now)
            old-date (sqlite-date (.minus now (java.time.Duration/ofHours 25)))
            recent-date (sqlite-date (.minus now (java.time.Duration/ofHours 23)))]

        ;; Pelada 1: Closed 25h ago, message NOT sent -> SHOULD be returned
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ?, vote_ended_message_sent = 0 WHERE id = 1" old-date])

        ;; Pelada 2: Closed 23h ago, message NOT sent -> SHOULD NOT be returned
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ?, vote_ended_message_sent = 0 WHERE id = 2" recent-date])

        ;; Pelada 3: Closed 25h ago, message ALREADY sent -> SHOULD NOT be returned
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ?, vote_ended_message_sent = 1 WHERE id = 3" old-date])

        (let [results (db.pelada/list-peladas-for-vote-notification db)]
          (is (= 1 (count results)))
          (is (= 1 (:id (first results)))))))

    (testing "Should return peladas for 12h and 23h reminders"
      (let [now (java.time.Instant/now)
            date-13h (sqlite-date (.minus now (java.time.Duration/ofHours 13)))
            date-23-5h (sqlite-date (.minus now (java.time.Duration/ofMinutes (+ (* 23 60) 30)))) ;; 23.5h ago
            org-id 1]

        ;; Pelada 4: Closed 13h ago, 12h reminder NOT sent -> SHOULD be returned as :12h
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ?, vote_reminder_12h_sent = 0 WHERE id = 4" date-13h])

        ;; Pelada 5: Closed 23.5h ago, 23h reminder NOT sent -> SHOULD be returned as :23h (and also :12h if not sent)
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ?, vote_reminder_12h_sent = 1, vote_reminder_23h_sent = 0 WHERE id = 5" date-23-5h])

        (let [results (db.pelada/list-peladas-for-vote-reminders db)
              p4-rem (first (filter #(= 4 (:id (:pelada %))) results))
              p5-rem (first (filter #(and (= 5 (:id (:pelada %))) (= :23h (:type %))) results))]
          (is (some? p4-rem))
          (is (= :12h (:type p4-rem)))
          (is (some? p5-rem))
          (is (= :23h (:type p5-rem))))))))
