(ns api-peladaapp.finance-adapter-test
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [clojure.test :refer [deftest is testing]]))

(deftest transaction-adapter-test
  (testing "db->transaction and model->transaction-response with java.sql.Date"
    (let [payment-date (java.sql.Date/valueOf "2026-05-21")
          created-at (java.sql.Timestamp. (.getTime (java.sql.Date/valueOf "2026-05-21")))
          db-row {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                  :organization_id (parse-uuid "00000000-0000-0000-0000-000000000002")
                  :amount 50.0
                  :payment_date payment-date
                  :created_at created-at}
          model (adapter.finance/db->transaction db-row)
          response (adapter.finance/model->transaction-response model)]
      ;; The model should keep the java.sql.Date
      (is (= payment-date (:payment-date model)))
      ;; The response should have the string representation
      (is (= "2026-05-21" (:payment_date response)))
      (is (string? (:payment_date response))))))
