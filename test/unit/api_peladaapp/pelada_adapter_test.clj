(ns api-peladaapp.pelada-adapter-test
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]])
  (:import
   [java.sql Timestamp]
   [java.time Duration Instant LocalDateTime]))

(deftest test-model->response-voting-status
  (testing "Should return voting status and preserve scheduled_at when voting is open"
    (let [now (Instant/now)
          two-hours-ago (.minus now (Duration/ofHours 2))
          id (parse-uuid "00000000-0000-0000-0000-000000000001")
          org-id (parse-uuid "00000000-0000-0000-0000-000000000010")
          model {:id id
                 :organization-id org-id
                 :organization-name "Test Org"
                 :scheduled-at "2023-01-01T10:00:00Z"
                 :status "closed"
                 :closed-at two-hours-ago}
          response (adapter.pelada/model->response model)]
      (is (= "voting" (:status response)))
      (is (= "2023-01-01T10:00:00Z" (:scheduled_at response)))
      (is (= id (:id response)))
      (is (= org-id (:organization_id response)))
      (is (= "Test Org" (:organization_name response)))))

  (testing "Should preserve original status when voting is NOT open"
    (let [id (parse-uuid "00000000-0000-0000-0000-000000000001")
          org-id (parse-uuid "00000000-0000-0000-0000-000000000010")
          model {:id id
                 :organization-id org-id
                 :scheduled-at "2023-01-01T10:00:00Z"
                 :status "open"}
          response (adapter.pelada/model->response model)]
      (is (= "open" (:status response)))
      (is (= "2023-01-01T10:00:00Z" (:scheduled_at response))))))

(deftest test-model->response-scheduled-at-formatting
  (testing "Should format java.time.LocalDateTime scheduled-at as UTC ISO string"
    (let [dt (LocalDateTime/of 2026 6 3 19 0 0)
          model {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                 :organization-id (parse-uuid "00000000-0000-0000-0000-000000000010")
                 :scheduled-at dt}
          response (adapter.pelada/model->response model)]
      (is (= "2026-06-03T19:00:00Z" (:scheduled_at response)))))

  (testing "Should format java.sql.Timestamp scheduled-at as UTC ISO string"
    (let [ts (Timestamp/valueOf "2026-06-03 19:00:00")
          model {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                 :organization-id (parse-uuid "00000000-0000-0000-0000-000000000010")
                 :scheduled-at ts}
          response (adapter.pelada/model->response model)]
      (is (str/ends-with? (:scheduled_at response) "Z")))))
