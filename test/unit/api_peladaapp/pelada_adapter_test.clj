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

(deftest test-max-players-pelada-adapter-mapping
  (testing "db->model maps max_players"
    (let [db-row {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                  :organization_id (parse-uuid "00000000-0000-0000-0000-000000000010")
                  :max_players 14}
          model (adapter.pelada/db->model db-row)]
      (is (= 14 (:max-players model)))))

  (testing "create-request->model maps max_players"
    (let [req {:organization_id "00000000-0000-0000-0000-000000000010"
               :when "2026-06-03T19:00:00Z"
               :max_players 20}
          model (adapter.pelada/create-request->model req)]
      (is (= 20 (:max-players model)))))

  (testing "model->response maps max-players"
    (let [model {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                 :organization-id (parse-uuid "00000000-0000-0000-0000-000000000010")
                 :max-players 16}
          resp (adapter.pelada/model->response model)]
      (is (= 16 (:max_players resp))))))

(deftest test-location-pelada-adapter-mapping
  (testing "db->model maps location, confirmed_count and confirmed_preview"
    (let [db-row {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                  :organization_id (parse-uuid "00000000-0000-0000-0000-000000000010")
                  :location "Arena Vila Nova · Q2"
                  :confirmed_count 3
                  :confirmed_preview "Ana|Bia|Caio"}
          model (adapter.pelada/db->model db-row)]
      (is (= "Arena Vila Nova · Q2" (:location model)))
      (is (= 3 (:confirmed-count model)))
      (is (= "Ana|Bia|Caio" (:confirmed-preview model)))))

  (testing "db->model omits nil preview (no confirmations yet)"
    (let [model (adapter.pelada/db->model {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                                           :organization_id (parse-uuid "00000000-0000-0000-0000-000000000010")
                                           :location nil
                                           :confirmed_count 0
                                           :confirmed_preview nil})]
      (is (not (contains? model :location)))
      (is (= 0 (:confirmed-count model)))
      (is (not (contains? model :confirmed-preview)))))

  (testing "create-request->model maps location"
    (let [model (adapter.pelada/create-request->model {:organization_id "00000000-0000-0000-0000-000000000010"
                                                       :location "Quadra do Parque"})]
      (is (= "Quadra do Parque" (:location model)))))

  (testing "create-request->model omits location when not provided"
    (let [model (adapter.pelada/create-request->model {:organization_id "00000000-0000-0000-0000-000000000010"})]
      (is (not (contains? model :location)))))

  (testing "update-request->model maps location"
    (let [model (adapter.pelada/update-request->model {:location "Nova Arena"})]
      (is (= "Nova Arena" (:location model)))))

  (testing "model->response maps location and confirmation summary"
    (let [model {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                 :organization-id (parse-uuid "00000000-0000-0000-0000-000000000010")
                 :scheduled-at "2026-06-03T19:00:00Z"
                 :status "attendance"
                 :location "Rua das Flores, 10"
                 :confirmed-count 2
                 :confirmed-preview "Ana|Bia"}
          resp (adapter.pelada/model->response model)]
      (is (= "Rua das Flores, 10" (:location resp)))
      (is (= 2 (:confirmed_count resp)))
      (is (= "Ana|Bia" (:confirmed_preview resp)))))

  (testing "model->response omits location when absent"
    (let [resp (adapter.pelada/model->response {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                                                :organization-id (parse-uuid "00000000-0000-0000-0000-000000000010")
                                                :scheduled-at "2026-06-03T19:00:00Z"
                                                :status "attendance"})]
      (is (not (contains? resp :location))))))
