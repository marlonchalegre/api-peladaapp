(ns api-peladaapp.finance-calculation-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest test-finance-reversal-scenarios
  (let [app (-> th/*test-system* :app :handler)
        admin-token (th/register-and-login! app {:name "Admin User" :email "admin@test.com" :password "test1234"})

        ;; Create Organization via admin
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Calculation Test Org"})
                          ((th/auth-cookie admin-token))))
        org-id (:id (th/decode-body org-resp))]

    (testing "Scenario 1: Income Reversal"
      ;; 1. Start: Balance 0, Income 0, Expense 0 (already verified by org creation)

      ;; 2. Player x made a deposit of 10
      (let [resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/transactions"))
                          (mock/json-body {:amount 10.0 :type "income" :category "other" :description "Deposit" :payment_date "2026-03-28"})
                          ((th/auth-cookie admin-token))))
            tx-id (:id (th/decode-body resp))]
        (is (= 201 (:status resp)))

        ;; Verify Summary: Balance 10, Income 10, Expense 0
        (let [summary (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/summary"))
                                               ((th/auth-cookie admin-token)))))]
          (is (= 10.0 (double (:total_income summary))))
          (is (= 0.0 (double (:total_expense summary))))
          (is (= 10.0 (double (:total_balance summary)))))

        ;; 3. Reverse the transaction
        (let [rev-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/transactions/" tx-id "/reverse"))
                                ((th/auth-cookie admin-token))))]
          (is (= 200 (:status rev-resp)))

          ;; Verify Summary: Balance 0, Income 0, Expense 0
          (let [summary (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/summary"))
                                                 ((th/auth-cookie admin-token)))))]
            (is (= 0.0 (double (:total_income summary))))
            (is (= 0.0 (double (:total_expense summary))))
            (is (= 0.0 (double (:total_balance summary))))))))

    (testing "Scenario 2: Expense Reversal"
      ;; 1. We bought a ball for 100
      (let [resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/transactions"))
                          (mock/json-body {:amount 100.0 :type "expense" :category "equipment" :description "Ball" :payment_date "2026-03-28"})
                          ((th/auth-cookie admin-token))))
            tx-id (:id (th/decode-body resp))]
        (is (= 201 (:status resp)))

        ;; Verify Summary: Balance -100, Income 0, Expense 100
        (let [summary (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/summary"))
                                               ((th/auth-cookie admin-token)))))]
          (is (= 0.0 (double (:total_income summary))))
          (is (= 100.0 (double (:total_expense summary))))
          (is (= -100.0 (double (:total_balance summary)))))

        ;; 2. Reverse the transaction (returned the ball)
        (let [rev-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/transactions/" tx-id "/reverse"))
                                ((th/auth-cookie admin-token))))]
          (is (= 200 (:status rev-resp)))

          ;; Verify Summary: Balance 0, Income 0, Expense 0
          (let [summary (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/summary"))
                                                 ((th/auth-cookie admin-token)))))]
            (is (= 0.0 (double (:total_income summary))))
            (is (= 0.0 (double (:total_expense summary))))
            (is (= 0.0 (double (:total_balance summary))))))))))
