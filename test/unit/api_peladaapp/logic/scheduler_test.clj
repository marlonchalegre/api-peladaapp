(ns api-peladaapp.logic.scheduler-test
  (:require
   [api-peladaapp.logic.scheduler :as scheduler]
   [clojure.test :refer [deftest is testing]])
  (:import
   [java.time ZoneId ZonedDateTime]))

(deftest test-reminder-type->db-type
  (testing "remaps keyword reminder types to string enum values"
    (is (= "vote_30m" (#'scheduler/reminder-type->db-type :vote_30m)))
    (is (= "vote_12h" (#'scheduler/reminder-type->db-type :vote_12h)))
    (is (= "vote_23h" (#'scheduler/reminder-type->db-type :vote_23h)))
    (is (= "attendance" (#'scheduler/reminder-type->db-type :attendance)))
    (is (= "vote_ended" (#'scheduler/reminder-type->db-type :vote_ended)))
    (is (= "other" (#'scheduler/reminder-type->db-type :other)))))

(deftest test-should-send-attendance-reminder?
  (testing "should-send-attendance-reminder? logic"
    (let [tz (ZoneId/of "America/Sao_Paulo")]
      (testing "returns false if not at 10h or 18h"
        (let [now (ZonedDateTime/of 2026 5 22 9 0 0 0 tz)]
          (is (false? (#'scheduler/should-send-attendance-reminder? nil now)))))
      (testing "returns true if at 10h/18h and never sent"
        (let [now (ZonedDateTime/of 2026 5 22 10 0 0 0 tz)]
          (is (true? (#'scheduler/should-send-attendance-reminder? nil now)))
          (is (true? (#'scheduler/should-send-attendance-reminder? "" now)))))
      (testing "returns false if at 10h but sent recently"
        (let [now (ZonedDateTime/of 2026 5 22 10 5 0 0 tz)
              last-sent "2026-05-22T10:00:00Z"]
          (is (false? (#'scheduler/should-send-attendance-reminder? last-sent now)))))
      (testing "returns true if at 18h and last sent was more than 4 hours ago"
        (let [now (ZonedDateTime/of 2026 5 22 18 0 0 0 tz)
              last-sent "2026-05-22T10:00:00Z"]
          (is (true? (#'scheduler/should-send-attendance-reminder? last-sent now))))))))
