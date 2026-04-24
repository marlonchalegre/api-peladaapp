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
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = 1" old-date])

        ;; Pelada 2: Closed 23h ago, message NOT sent -> SHOULD NOT be returned
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = 2" recent-date])

        ;; Pelada 3: Closed 25h ago, message ALREADY sent -> SHOULD NOT be returned
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = 3" old-date])
        (jdbc/execute! db ["INSERT INTO PeladaReminders (pelada_id, type) VALUES (3, 'vote_ended')"])

        (let [results (db.pelada/list-peladas-for-vote-notification db)]
          (is (= 1 (count results)))
          (is (= 1 (:id (first results)))))))

    (testing "Should return peladas for 30m, 12h and 23h reminders"
      (let [now (java.time.Instant/now)
            date-31m (sqlite-date (.minus now (java.time.Duration/ofMinutes 31)))
            date-13h (sqlite-date (.minus now (java.time.Duration/ofHours 13)))
            date-23-5h (sqlite-date (.minus now (java.time.Duration/ofMinutes (+ (* 23 60) 30)))) ;; 23.5h ago
            org-id 1]

        ;; Pelada 4: Closed 13h ago, 12h reminder NOT sent -> SHOULD be returned as :12h
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = 4" date-13h])
        (jdbc/execute! db ["INSERT INTO PeladaReminders (pelada_id, type) VALUES (4, 'vote_30m')"])

        ;; Pelada 5: Closed 23.5h ago, 23h reminder NOT sent -> SHOULD be returned as :23h (and also :12h if not sent)
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = 5" date-23-5h])
        (jdbc/execute! db ["INSERT INTO PeladaReminders (pelada_id, type) VALUES (5, 'vote_30m')"])
        (jdbc/execute! db ["INSERT INTO PeladaReminders (pelada_id, type) VALUES (5, 'vote_12h')"])

        ;; Pelada 6: Closed 31m ago, 30m reminder NOT sent -> SHOULD be returned as :30m
        (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)
        (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = 6" date-31m])

        (let [results (db.pelada/list-peladas-for-vote-reminders db)
              p4-rem (first (filter #(and (= 4 (:id (:pelada %))) (= :12h (:type %))) results))
              p5-rem (first (filter #(and (= 5 (:id (:pelada %))) (= :23h (:type %))) results))
              p6-rem (first (filter #(and (= 6 (:id (:pelada %))) (= :30m (:type %))) results))]
          (is (some? p4-rem))
          (is (= :12h (:type p4-rem)))
          (is (some? p5-rem))
          (is (= :23h (:type p5-rem)))
          (is (some? p6-rem))
          (is (= :30m (:type p6-rem))))))))
