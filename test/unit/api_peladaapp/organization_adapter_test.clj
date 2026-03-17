(ns api-peladaapp.organization-adapter-test
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [clojure.test :refer [deftest is testing]]))

(deftest test-db->model-waha-defaults
  (testing "Should convert NULL WAHA fields to false (disabled by default)"
    (let [db-row {:id 1 :name "Test Org"
                  :waha_api_url nil :waha_instance nil :waha_group_id nil
                  :waha_enabled nil :waha_start_msg_enabled nil
                  :waha_end_msg_enabled nil :waha_attendance_reminder_enabled nil
                  :waha_vote_reminder_enabled nil :waha_vote_ended_msg_enabled nil}
          model (adapter.organization/db->model db-row)]
      (is (false? (:waha-enabled model)))
      (is (false? (:waha-start-msg-enabled model)))
      (is (false? (:waha-end-msg-enabled model)))
      (is (false? (:waha-attendance-reminder-enabled model)))
      (is (false? (:waha-vote-reminder-enabled model)))
      (is (false? (:waha-vote-ended-msg-enabled model)))))

  (testing "Should convert 1 to true and 0 to false for WAHA fields"
    (let [db-row {:id 1 :name "Test Org"
                  :waha_enabled 1 :waha_start_msg_enabled 0}
          model (adapter.organization/db->model db-row)]
      (is (true? (:waha-enabled model)))
      (is (false? (:waha-start-msg-enabled model))))))
