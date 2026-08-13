(ns api-peladaapp.logic.scheduler-unit-test
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.reminder :as db.reminder]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.notifications :as notifications]
   [api-peladaapp.logic.scheduler :as scheduler]
   [clojure.test :refer [deftest is testing]])

  (:import
   [java.time ZoneId ZonedDateTime]))

(deftest test-should-send-attendance-reminder
  (let [should-send? #'scheduler/should-send-attendance-reminder?
        zone (ZoneId/of "America/Sao_Paulo")
        time-10h (ZonedDateTime/of 2023 1 1 10 0 0 0 zone)
        time-9h (ZonedDateTime/of 2023 1 1 9 0 0 0 zone)]
    (testing "not reminder hour returns false"
      (is (false? (should-send? nil time-9h))))

    (testing "reminder hour with no last-sent returns true"
      (is (true? (should-send? nil time-10h)))
      (is (true? (should-send? "" time-10h))))

    (testing "reminder hour with last-sent > 4 hours returns true"
      (let [last-sent "2023-01-01T05:00:00Z"]
        (is (true? (should-send? last-sent time-10h)))))

    (testing "reminder hour with last-sent <= 4 hours returns false"
      (let [last-sent (.minusHours time-10h 3)]
        (is (false? (should-send? (helpers.time/to-utc-timestamp-str last-sent) time-10h)))))

    (testing "invalid last-sent string catches exception and returns true"
      (is (true? (should-send? "invalid-date" time-10h))))))

(deftest test-reminder-type-to-db-type
  (let [type->db #'scheduler/reminder-type->db-type]
    (is (= "vote_30m" (type->db :vote_30m)))
    (is (= "vote_12h" (type->db :vote_12h)))
    (is (= "vote_23h" (type->db :vote_23h)))
    (is (= "attendance" (type->db :attendance)))
    (is (= "vote_ended" (type->db :vote_ended)))
    (is (= "other" (type->db :other)))))

(deftest test-run-attendance-reminders
  (let [run-reminders! #'scheduler/run-attendance-reminders!
        zone (ZoneId/of "America/Sao_Paulo")
        time-10h (ZonedDateTime/of 2023 1 1 10 0 0 0 zone)
        org-enabled {:id (random-uuid) :name "Org Enabled" :waha-attendance-reminder-enabled true}
        org-disabled {:id (random-uuid) :name "Org Disabled" :waha-attendance-reminder-enabled false}
        pelada {:id (random-uuid) :status "attendance"}
        db nil]
    (testing "iterates and sends reminders when enabled and due"
      (let [send-called (atom false)
            insert-called (atom false)]
        (with-redefs [db.organization/list-organizations (fn [_] [org-disabled org-enabled])
                      db.pelada/list-peladas (fn [org-id _ _ _]
                                               (is (= (:id org-enabled) org-id))
                                               [pelada])
                      db.reminder/get-last-reminder-at (fn [p-id r-type _]
                                                         (is (= (:id pelada) p-id))
                                                         (is (= "attendance" r-type))
                                                         nil)
                      db.attendance/list-pending-mensalistas-by-pelada (fn [p-id _]
                                                                         (is (= (:id pelada) p-id))
                                                                         [{:id (random-uuid)}])
                      notifications/send-notification! (fn [org-id type payload _]
                                                         (is (= (:id org-enabled) org-id))
                                                         (is (= :attendance-reminder type))
                                                         (is (= (:id pelada) (:pelada-id payload)))
                                                         (reset! send-called true))
                      db.reminder/insert-reminder! (fn [p-id r-type _]
                                                     (is (= (:id pelada) p-id))
                                                     (is (= "attendance" r-type))
                                                     (reset! insert-called true))]
          (run-reminders! db time-10h)
          (is (true? @send-called))
          (is (true? @insert-called)))))

    (testing "skips reminder when pending list is empty"
      (let [send-called (atom false)]
        (with-redefs [db.organization/list-organizations (fn [_] [org-enabled])
                      db.pelada/list-peladas (fn [_ _ _ _] [pelada])
                      db.reminder/get-last-reminder-at (fn [_ _ _] nil)
                      db.attendance/list-pending-mensalistas-by-pelada (fn [_ _] [])
                      notifications/send-notification! (fn [& _] (reset! send-called true))]
          (run-reminders! db time-10h)
          (is (false? @send-called)))))))

(deftest test-check-vote-ended-scenarios
  (let [check-ended! #'scheduler/check-vote-ended!
        org-id (random-uuid)
        org {:id org-id :name "Org 1" :waha-vote-ended-msg-enabled true :waha-vote-reminder-enabled true}
        pelada {:id (random-uuid) :organization-id org-id}
        player-id (random-uuid)
        ranking [{:player-id player-id :avg-stars 4.5}]
        db nil]
    (testing "no peladas or reminders"
      (with-redefs [db.pelada/list-peladas-for-vote-notification (fn [_] [])
                    db.pelada/list-peladas-for-vote-reminders (fn [_] [])]
        (is (nil? (check-ended! db)))))

    (testing "process ended vote with update and notification"
      (let [grade-updated (atom nil)
            notification-sent (atom false)
            reminder-inserted (atom false)]
        (with-redefs [db.pelada/list-peladas-for-vote-notification (fn [_] [pelada])
                      db.organization/get-organization (fn [o-id _] (is (= org-id o-id)) org)
                      db.vote/list-ranking-by-pelada (fn [p-id _] (is (= (:id pelada) p-id)) ranking)
                      db.player/get-player (fn [p-id _] (is (= player-id p-id)) {:id player-id :grade 4.8})
                      db.player/update-player-grade (fn [p-id new-grade _]
                                                      (is (= player-id p-id))
                                                      (reset! grade-updated new-grade))
                      notifications/send-notification! (fn [o-id type payload _]
                                                         (is (= org-id o-id))
                                                         (is (= :vote-ended type))
                                                         (is (= (:id pelada) (:pelada-id payload)))
                                                         (reset! notification-sent true))
                      db.reminder/insert-reminder! (fn [p-id r-type _]
                                                     (is (= (:id pelada) p-id))
                                                     (is (= "vote_ended" r-type))
                                                     (reset! reminder-inserted true))
                      db.pelada/list-peladas-for-vote-reminders (fn [_] [])]
          (check-ended! db)
          (is (some? @grade-updated))
          (is (true? @notification-sent))
          (is (true? @reminder-inserted)))))

    (testing "process ended vote with WAHA msg disabled"
      (let [notification-sent (atom false)]
        (with-redefs [db.pelada/list-peladas-for-vote-notification (fn [_] [pelada])
                      db.organization/get-organization (fn [_ _] (assoc org :waha-vote-ended-msg-enabled false))
                      db.vote/list-ranking-by-pelada (fn [_ _] ranking)
                      db.player/get-player (fn [_ _] {:id player-id :grade nil}) ; triggers default grade 5.0 path
                      db.player/update-player-grade (fn [& _] nil)
                      notifications/send-notification! (fn [& _] (reset! notification-sent true))
                      db.reminder/insert-reminder! (fn [& _] nil)
                      db.pelada/list-peladas-for-vote-reminders (fn [_] [])]
          (check-ended! db)
          (is (false? @notification-sent)))))

    (testing "process vote reminders when enabled"
      (let [notification-sent (atom false)
            reminder-inserted (atom false)]
        (with-redefs [db.pelada/list-peladas-for-vote-notification (fn [_] [])
                      db.pelada/list-peladas-for-vote-reminders (fn [_] [{:pelada pelada :type :vote_12h}])
                      db.organization/get-organization (fn [_ _] org)
                      db.vote/list-pending-voters-by-pelada (fn [p-id _] (is (= (:id pelada) p-id)) [{:id (random-uuid)}])
                      notifications/send-notification! (fn [o-id type _payload _]
                                                         (is (= org-id o-id))
                                                         (is (= :vote-reminder type))
                                                         (reset! notification-sent true))
                      db.reminder/insert-reminder! (fn [p-id r-type _]
                                                     (is (= (:id pelada) p-id))
                                                     (is (= "vote_12h" r-type))
                                                     (reset! reminder-inserted true))]
          (check-ended! db)
          (is (true? @notification-sent))
          (is (true? @reminder-inserted)))))

    (testing "process vote reminders when disabled or no pending voters"
      (let [notification-sent (atom false)]
        (with-redefs [db.pelada/list-peladas-for-vote-notification (fn [_] [])
                      db.pelada/list-peladas-for-vote-reminders (fn [_] [{:pelada pelada :type :vote_12h}
                                                                         {:pelada pelada :type :vote_23h}])
                      db.organization/get-organization (fn [_ _] (assoc org :waha-vote-reminder-enabled false))
                      db.vote/list-pending-voters-by-pelada (fn [_ _] [])
                      notifications/send-notification! (fn [& _] (reset! notification-sent true))
                      db.reminder/insert-reminder! (fn [& _] nil)]
          (check-ended! db)
          (is (false? @notification-sent)))))))

(deftest test-execute-tasks
  (testing "execute-tasks! calls runners and logs"
    (let [run-called (atom false)
          ended-called (atom false)
          db nil]
      (with-redefs [scheduler/br-now (fn [] (ZonedDateTime/now (ZoneId/of "America/Sao_Paulo")))
                    scheduler/run-attendance-reminders! (fn [_ _] (reset! run-called true))
                    scheduler/run-priority-ending-reminders! (fn [_ _] nil)
                    scheduler/check-vote-ended! (fn [_] (reset! ended-called true))]
        (scheduler/execute-tasks! db)
        (is (true? @run-called))
        (is (true? @ended-called)))))

  (testing "execute-tasks! catches exceptions"
    (let [db nil]
      (with-redefs [scheduler/br-now (fn [] (ZonedDateTime/now (ZoneId/of "America/Sao_Paulo")))
                    scheduler/run-attendance-reminders! (fn [_ _] (throw (Exception. "Failed execution")))]
        ;; Should not throw exception to caller
        (is (nil? (scheduler/execute-tasks! db)))))))
