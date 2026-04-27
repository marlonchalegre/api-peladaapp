(ns api-peladaapp.scheduler-logic-test
  (:require
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.reminder :as db.reminder]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.scheduler :as scheduler]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]))

(use-fixtures :once th/test-system-fixture)

(defn- sqlite-date [instant]
  (let [formatter (-> (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")
                      (.withZone (java.time.ZoneId/of "UTC")))]
    (.format formatter instant)))

(deftest test-scheduler-updates-grades-without-waha
  (let [db-file (:db-file th/*test-system*)
        db (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (testing "execute-tasks! updates grades and inserts reminders even if WAHA is disabled"
      ;; 1. Setup organization with WAHA notifications explicitly disabled
      (let [org-id (db.organization/insert-organization {:name "Org WAHA Disabled" :owner-id nil} db)]
        (db.organization/update-organization
         org-id
         {:waha-enabled true
          :waha-vote-ended-msg-enabled false
          :waha-vote-reminder-enabled false}
         db)

        ;; 2. Setup user and player
        (let [voter-user-id (db.user/insert-user {:email "voter@test.com" :password "pwd" :name "Voter" :username "voter"} db)
              voter-player-id (db.player/insert-player {:organization-id org-id :user-id voter-user-id :grade 5.0} db)
              target-user-id (db.user/insert-user {:email "target@test.com" :password "pwd" :name "Target" :username "target"} db)
              target-player-id (db.player/insert-player {:organization-id org-id :user-id target-user-id :grade 5.0} db)]
              
              ;; 3. Create a Pelada closed 25h ago
              (let [now (java.time.Instant/now)
                    old-date (sqlite-date (.minus now (java.time.Duration/ofHours 25)))
                    pelada-id (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)]
                (jdbc/execute! db ["UPDATE Peladas SET status = 'closed', closed_at = ? WHERE id = ?" old-date pelada-id])

                  (jdbc/execute! db ["INSERT INTO peladaattendance (pelada_id, player_id, status, voting_enabled) VALUES (?, ?, 'confirmed', 1)" pelada-id target-player-id])
                  (jdbc/execute! db ["INSERT INTO peladaattendance (pelada_id, player_id, status, voting_enabled) VALUES (?, ?, 'confirmed', 1)" pelada-id voter-player-id])
                  
                  ;; 4. Add a vote for the target player (e.g., 1 star to drastically change grade)
                  (db.vote/insert-vote {:pelada-id pelada-id :voter-id voter-player-id :target-id target-player-id :stars 1} db)

                  ;; Ensure reminder doesn't exist yet
                  (is (nil? (db.reminder/get-last-reminder-at pelada-id "vote_ended" db)))
                  
                  ;; Ensure initial grade is 5.0
                  (is (= 5.0 (:grade (db.player/get-player target-player-id db))))

                  ;; 5. Run scheduler
                  (scheduler/execute-tasks! db)

                  ;; 6. Verify grade was updated
                  (let [new-grade (:grade (db.player/get-player target-player-id db))]
                    (is (not= 5.0 new-grade) "Grade should have been recalculated based on the vote"))

                  ;; 7. Verify reminder was inserted to prevent infinite loops
                  (is (some? (db.reminder/get-last-reminder-at pelada-id "vote_ended" db))
                      "PeladaReminder 'vote_ended' should have been inserted even with WAHA disabled")))))))
