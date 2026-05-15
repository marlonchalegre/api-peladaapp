(ns api-peladaapp.pelada-adapter-test
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [clojure.test :refer [deftest is testing]])
  (:import
   [java.time Duration Instant]))

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
