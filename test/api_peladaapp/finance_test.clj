(ns api-peladaapp.finance-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest test-finance-endpoints
  (let [app (-> th/*test-system* :app :handler)
        db-comp (:database th/*test-system*)
        db-val (:database db-comp)
        ds (if (fn? db-val) (db-val) db-val)
        admin-token (th/register-and-login! app {:name "Admin User" :email "admin@test.com" :password "test1234"})
        admin-id (th/user-id-by-email ds "admin@test.com")

        member-token (th/register-and-login! app {:name "Member User" :email "member@test.com" :password "test1234"})
        member-id (th/user-id-by-email ds "member@test.com")

        ;; Create Organization via admin
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Finance Test Org"})
                          ((th/auth-header admin-token))))
        org-id (:id (th/decode-body org-resp))]

    ;; Add member to org
    (app (-> (mock/request :post (str "/api/organizations/" org-id "/players"))
             (mock/json-body {:player_id member-id})
             ((th/auth-header admin-token))))

    (testing "Finance settings - Admin can update and view, member is blocked"
      ;; Get Finance (Member)
      (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance"))
                          ((th/auth-header member-token))))]
        (is (= 403 (:status resp))))

      ;; Get Finance (Admin) - should be 200 and return defaults
      (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance"))
                          ((th/auth-header admin-token))))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= 0.0 (double (:mensalista_price body))))
        (is (= 0.0 (double (:diarista_price body)))))

      ;; Update Finance (Admin)
      (let [resp (app (-> (mock/request :put (str "/api/organizations/" org-id "/finance"))
                          (mock/json-body {:mensalista_price 100.0 :diarista_price 20.0 :currency "BRL"})
                          ((th/auth-header admin-token))))]
        (is (= 200 (:status resp))))

      ;; Verify update
      (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance"))
                          ((th/auth-header admin-token))))
            body (th/decode-body resp)]
        (is (= 100.0 (double (:mensalista_price body))))
        (is (= 20.0 (double (:diarista_price body))))))

    (testing "Transactions - Admin can add and list, member is blocked"
      ;; Add transaction (Member)
      (let [resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/transactions"))
                          (mock/json-body {:amount 50.0 :type "income" :category "other" :payment_date "2026-03-27"})
                          ((th/auth-header member-token))))]
        (is (= 403 (:status resp))))

      ;; Add transaction (Admin)
      (let [resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/transactions"))
                          (mock/json-body {:amount 50.0 :type "income" :category "other" :payment_date "2026-03-27"})
                          ((th/auth-header admin-token))))]
        (is (= 201 (:status resp))))

      ;; List transactions (Admin)
      (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/transactions"))
                          ((th/auth-header admin-token))))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1 (count body)))
        (is (= 50.0 (double (:amount (first body)))))))

    (testing "Finance Summary"
      (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/summary"))
                          ((th/auth-header admin-token))))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= 50.0 (double (:total_income body))))
        (is (= 0.0 (double (:total_expense body))))
        (is (= 50.0 (double (:total_balance body))))))

    (testing "Monthly Payments and Reversal - Regression Test"
      (let [year 2026
            month 3
            ;; Creator is already a mensalista, let's use them
            player-id (th/player-id-by-user-id ds admin-id org-id)]

        ;; Initially unpaid
        (let [resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/monthly-payments"))
                            (mock/query-string {:year year :month month})
                            ((th/auth-header admin-token))))
              payments (th/decode-body resp)
              payment (first (filter #(= (:player_id %) player-id) payments))]
          (is (= 200 (:status resp)))
          (is (false? (:paid payment))))

        ;; Mark as paid
        (let [resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/monthly-payments"))
                            (mock/json-body {:player_id player-id
                                             :year year
                                             :month month
                                             :paid true
                                             :amount 100.0})
                            ((th/auth-header admin-token))))
              body (th/decode-body resp)
              tx-id (:transaction_id body)]
          (is (= 200 (:status resp)))
          (is (some? tx-id))

          ;; Verify it is now paid
          (let [p-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/monthly-payments"))
                                (mock/query-string {:year year :month month})
                                ((th/auth-header admin-token))))
                payments (th/decode-body p-resp)
                payment (first (filter #(= (:player_id %) player-id) payments))]
            (is (true? (:paid payment)))
            (is (= tx-id (:transaction_id payment)))

            ;; Reverse the transaction directly
            (let [rev-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/finance/transactions/" tx-id "/reverse"))
                                    ((th/auth-header admin-token))))]
              (is (= 200 (:status rev-resp))))

            ;; Verify if it is now unpaid
            (let [final-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/finance/monthly-payments"))
                                      (mock/query-string {:year year :month month})
                                      ((th/auth-header admin-token))))
                  final-payments (th/decode-body final-resp)
                  final-payment (first (filter #(= (:player_id %) player-id) final-payments))]
              (is (false? (:paid final-payment)) "Monthly payment should be unpaid after transaction reversal")
              (is (nil? (:transaction_id final-payment)) "Monthly payment should not have transaction_id after reversal"))))))))