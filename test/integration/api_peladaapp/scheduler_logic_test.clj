(ns api-peladaapp.scheduler-logic-test
  (:require
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.reminder :as db.reminder]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.scheduler :as scheduler]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]))

(use-fixtures :once th/test-system-fixture)

(deftest test-scheduler-updates-grades-without-waha
  (let [db-val (-> th/*test-system* :database :database)
        db (if (fn? db-val) (db-val) db-val)]
    (testing "execute-tasks! updates grades and inserts reminders even if WAHA is disabled"
      ;; 1. Setup organization with WAHA notifications explicitly disabled
      (let [org-id (db.organization/insert-organization {:name "Org WAHA Disabled" :owner-id nil} db)
            _ (db.organization/update-organization
               org-id
               {:waha-enabled true
                :waha-vote-ended-msg-enabled false
                :waha-vote-reminder-enabled false}
               db)

            ;; 2. Setup user and player
            voter-user-id (db.user/insert-user {:email "voter@test.com" :password "pwd" :name "Voter" :username "voter"} db)
            voter-player-id (db.player/insert-player {:organization-id org-id :user-id voter-user-id :grade 5.0} db)
            target-user-id (db.user/insert-user {:email "target@test.com" :password "pwd" :name "Target" :username "target"} db)
            target-player-id (db.player/insert-player {:organization-id org-id :user-id target-user-id :grade 5.0} db)

            ;; 3. Create a Pelada closed 25h ago
            now (java.time.OffsetDateTime/now)
            old-date (.minus now (java.time.Duration/ofHours 25))
            pelada-id (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)]

        (jdbc/execute! db (hsql/format (-> (h/update :Peladas)
                                           (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast old-date :timestamp]]})
                                           (h/where [:= :id (misc/as-uuid pelada-id)]))))

        (jdbc/execute! db (hsql/format (-> (h/insert-into :Attendance)
                                           (h/values [{:pelada_id (misc/as-uuid pelada-id) :player_id (misc/as-uuid target-player-id) :status [:cast "confirmed" :attendance_status] :voting_enabled true}
                                                      {:pelada_id (misc/as-uuid pelada-id) :player_id (misc/as-uuid voter-player-id) :status [:cast "confirmed" :attendance_status] :voting_enabled true}]))))

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
            "PeladaReminder 'vote_ended' should have been inserted even with WAHA disabled")))))

(deftest test-scheduler-vote-reminder-bounds
  (let [db-val (-> th/*test-system* :database :database)
        db (if (fn? db-val) (db-val) db-val)]
    (testing "list-peladas-for-vote-reminders only returns peladas within the 1-hour windows"
      (let [org-id (db.organization/insert-organization {:name "Org Bounds Test" :owner-id nil} db)
            now (java.time.OffsetDateTime/now)
            ;; Helper to create pelada at specific time
            create-pelada (fn [duration]
                            (let [d (.minus now duration)
                                  p-id (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)]
                              (jdbc/execute! db (hsql/format (-> (h/update :Peladas)
                                                                 (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast d :timestamp]]})
                                                                 (h/where [:= :id (misc/as-uuid p-id)]))))
                              p-id))
            ;; Inside windows
            p-30m-in (create-pelada (java.time.Duration/ofMinutes 45))
            p-12h-in (create-pelada (java.time.Duration/ofMinutes (+ (* 12 60) 30)))
            p-23h-in (create-pelada (java.time.Duration/ofMinutes (+ (* 23 60) 30)))
            ;; Outside windows
            _p-30m-out (create-pelada (java.time.Duration/ofMinutes 95))
            _p-12h-out (create-pelada (java.time.Duration/ofMinutes (+ (* 13 60) 30)))
            _p-23h-out (create-pelada (java.time.Duration/ofMinutes (+ (* 24 60) 30)))

            reminders (db.pelada/list-peladas-for-vote-reminders db)
            ;; Filter out reminders from other tests
            my-reminders (filter #(= org-id (-> % :pelada :organization-id)) reminders)
            by-type (group-by :type my-reminders)
            ids-by-type (fn [t] (set (map #(-> % :pelada :id) (get by-type t))))]

        (is (= #{p-30m-in} (ids-by-type :vote_30m)) "Only 45m pelada should be in 30m window")
        (is (= #{p-12h-in} (ids-by-type :vote_12h)) "Only 12.5h pelada should be in 12h window")
        (is (= #{p-23h-in} (ids-by-type :vote_23h)) "Only 23.5h pelada should be in 23h window")))))

(deftest test-scheduler-vote-reminders-workflow
  (let [db-val (-> th/*test-system* :database :database)
        db (if (fn? db-val) (db-val) db-val)]
    (testing "execute-tasks! sends and records vote reminders without throwing database constraint errors"
      (let [org-id (db.organization/insert-organization {:name "Org Vote Reminder Test" :owner-id nil} db)
            _ (db.organization/update-organization
               org-id
               {:waha-enabled true
                :waha-vote-reminder-enabled true}
               db)

            ;; Setup users/players (voter and target)
            voter-user-id (db.user/insert-user {:email "voter-rem@test.com" :password "pwd" :name "Voter Rem" :username "voter_rem"} db)
            voter-player-id (db.player/insert-player {:organization-id org-id :user-id voter-user-id :grade 5.0} db)
            target-user-id (db.user/insert-user {:email "target-rem@test.com" :password "pwd" :name "Target Rem" :username "target_rem"} db)
            target-player-id (db.player/insert-player {:organization-id org-id :user-id target-user-id :grade 5.0} db)

            ;; Create a Pelada closed 12.5h ago
            now (java.time.OffsetDateTime/now)
            closed-at (.minus now (java.time.Duration/ofMinutes (+ (* 12 60) 30)))
            pelada-id (db.pelada/insert-pelada {:organization-id org-id :status "closed"} db)]

        (jdbc/execute! db (hsql/format (-> (h/update :Peladas)
                                           (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast closed-at :timestamp]]})
                                           (h/where [:= :id (misc/as-uuid pelada-id)]))))

        ;; Setup attendance to make target-player a pending voter
        (jdbc/execute! db (hsql/format (-> (h/insert-into :Attendance)
                                           (h/values [{:pelada_id (misc/as-uuid pelada-id) :player_id (misc/as-uuid target-player-id) :status [:cast "confirmed" :attendance_status] :voting_enabled true}
                                                      {:pelada_id (misc/as-uuid pelada-id) :player_id (misc/as-uuid voter-player-id) :status [:cast "confirmed" :attendance_status] :voting_enabled true}]))))

        ;; Ensure no reminder exists yet
        (is (nil? (db.reminder/get-last-reminder-at pelada-id "vote_12h" db)))

        ;; 5. Run scheduler
        (scheduler/execute-tasks! db)

        ;; 6. Verify reminder was successfully recorded in the database
        (is (some? (db.reminder/get-last-reminder-at pelada-id "vote_12h" db))
            "PeladaReminder 'vote_12h' should have been inserted")

        ;; 7. Run scheduler again and verify that it does not insert a duplicate or fail
        (let [before-count (:count (jdbc/execute-one! db (hsql/format (-> (h/select [[:count :*] :count])
                                                                          (h/from :PeladaReminders)
                                                                          (h/where [:= :pelada_id (misc/as-uuid pelada-id)] [:= :type [:cast "vote_12h" :reminder_type]])))
                                                     hsql/opts))]
          (scheduler/execute-tasks! db)
          (let [after-count (:count (jdbc/execute-one! db (hsql/format (-> (h/select [[:count :*] :count])
                                                                           (h/from :PeladaReminders)
                                                                           (h/where [:= :pelada_id (misc/as-uuid pelada-id)] [:= :type [:cast "vote_12h" :reminder_type]])))
                                                       hsql/opts))]
            (is (= before-count after-count) "Should not record duplicate reminders")))))))

(deftest test-priority-ending-reminder
  (let [db-val (-> th/*test-system* :database :database)
        db (if (fn? db-val) (db-val) db-val)]
    (testing "execute-tasks! sends priority ending reminder when limit threshold is reached"
      (let [org-id (db.organization/insert-organization {:name "Org Priority Ending" :owner-id nil} db)
            _ (db.organization/insert-default-feature-flags org-id db)
            _ (db.organization/update-organization
               org-id
               {:priority-confirmation-limit-hours 24
                :waha-enabled true
                :waha-attendance-reminder-enabled true}
               db)
            now (java.time.OffsetDateTime/now)
            scheduled-at (.plus now (java.time.Duration/ofHours 25))
            pelada-id (db.pelada/insert-pelada {:organization-id org-id :status "attendance"} db)]

        (jdbc/execute! db (hsql/format (-> (h/update :Peladas)
                                           (h/set {:status [:cast "attendance" :pelada_status] :scheduled_at [[:cast scheduled-at :timestamp]]})
                                           (h/where [:= :id (misc/as-uuid pelada-id)]))))

        (is (nil? (db.reminder/get-last-reminder-at pelada-id "priority_ending" db)))

        (scheduler/execute-tasks! db)

        (is (some? (db.reminder/get-last-reminder-at pelada-id "priority_ending" db))
            "PeladaReminder 'priority_ending' should have been inserted")))))

