(ns api-peladaapp.organization-adapter-test
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [clojure.test :refer [deftest is testing]]))

(deftest test-db->model-waha-defaults
  (testing "Should convert NULL WAHA fields to false (disabled by default)"
    (let [id (parse-uuid "00000000-0000-0000-0000-000000000001")
          db-row {:id id :name "Test Org"
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

  (testing "Should convert true/false for WAHA fields"
    (let [id (parse-uuid "00000000-0000-0000-0000-000000000001")
          db-row {:id id :name "Test Org"
                  :waha_enabled true :waha_start_msg_enabled false}
          model (adapter.organization/db->model db-row)]
      (is (true? (:waha-enabled model)))
      (is (false? (:waha-start-msg-enabled model))))))

(deftest substitution-adapter-test
  (testing "db->substitution and model->substitution-response with java.sql.Date"
    (let [start-date (java.sql.Date/valueOf "2026-05-01")
          db-row {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                  :organization_id (parse-uuid "00000000-0000-0000-0000-000000000002")
                  :permanent_player_id (parse-uuid "00000000-0000-0000-0000-000000000003")
                  :temporary_player_id (parse-uuid "00000000-0000-0000-0000-000000000004")
                  :start_date start-date
                  :active 1}
          model (adapter.organization/db->substitution db-row)
          response (adapter.organization/model->substitution-response model)]
      (is (= "2026-05-01" (:start-date model))) ;; db->substitution already converts to str
      (is (= "2026-05-01" (:start_date response)))
      (is (true? (:active model))))))

(deftest test-default-max-players-adapter-mapping
  (testing "db->model maps default_max_players"
    (let [db-row {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                  :name "Test Org"
                  :default_max_players 16}
          model (adapter.organization/db->model db-row)]
      (is (= 16 (:default-max-players model)))))

  (testing "update-request->model maps default_max_players"
    (let [req {:name "Updated Org"
               :default_max_players 14}
          model (adapter.organization/update-request->model req)]
      (is (= 14 (:default-max-players model)))))

  (testing "model->response maps default-max-players"
    (let [model {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                 :name "Test Org"
                 :default-max-players 18}
          resp (adapter.organization/model->response model)]
      (is (= 18 (:default_max_players resp))))))
