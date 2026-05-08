(ns api-peladaapp.handlers.finance
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [api-peladaapp.db.finance :as db.finance]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :refer [created ok]]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.finance :as logic.finance]
   [next.jdbc :as jdbc]))

(defn get-finance [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (adapter.finance/model->finance-response (db.finance/get-organization-finance org-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-finance [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (let [model (adapter.finance/payload->finance body)]
           (db.finance/upsert-organization-finance org-id model db)
           (ok {:message "Finance settings updated"})))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-transactions [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             {:keys [page per-page]} (pagination/parse-pagination-params (:query-params request))
             offset (* (dec page) per-page)]
         (auth/require-organization-admin! user-id org-id db)
         (let [txs (db.finance/list-transactions org-id per-page offset db)
               total-count (db.finance/count-transactions org-id db)
               pagination-headers (:headers (pagination/with-pagination-headers nil total-count page per-page))]
           (ok (map adapter.finance/model->transaction-response txs) pagination-headers)))
       (catch Exception e (exception/api-exception-handler e))))

(defn add-transaction [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (let [transaction (assoc (adapter.finance/payload->transaction body)
                                  :organization-id org-id
                                  :created-by user-id)
               stored-tx (db.finance/add-transaction transaction db)
               model-tx (adapter.finance/db->transaction stored-tx)]
           (created (adapter.finance/model->transaction-response model-tx))))
       (catch Exception e (exception/api-exception-handler e))))

(defn reverse-transaction [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             tx-id (Integer/parseInt (str (get-in request [:params :tx_id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (db.finance/reverse-transaction tx-id db)
         (ok {:message "Transaction reversed"}))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-monthly-payments [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             year (Integer/parseInt (get-in request [:query-params "year"]))
             month (Integer/parseInt (get-in request [:query-params "month"]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (map adapter.finance/model->monthly-payment-response (db.finance/get-monthly-payments org-id year month db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn mark-monthly-payment [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (jdbc/with-transaction [tx db]
           (let [payment-req (adapter.finance/payload->monthly-payment body)
                 paid? (:paid payment-req)
                 player-id (:player-id payment-req)
                 year (:year payment-req)
                 month (:month payment-req)
                 payment-date (or (:payment_date body) (str (java.time.LocalDate/now)))

                 org-finance (db.finance/get-organization-finance org-id tx)

                 fine (if paid?
                        (if (contains? body :fine_amount)
                          (double (or (:fine_amount body) 0.0))
                          (logic.finance/calculate-monthly-fine
                           year month payment-date
                           (:monthly-fine-amount org-finance)
                           (:monthly-cut-off-day org-finance)))
                        0.0)

                 amount (or (:amount body)
                            (+ (:mensalista-price org-finance) fine))
                 ;; Find existing payment record to see if there's a transaction to reverse
                 existing-payments (db.finance/get-monthly-payments org-id year month tx)
                 existing (first (filter (fn [p] (and (= (some-> p :player-id long) (some-> player-id long))
                                                      (= (some-> p :year long) (some-> year long))
                                                      (= (some-> p :month long) (some-> month long))))
                                         existing-payments))

                 existing-tx-id (:transaction-id existing)
                 existing-fine-tx-id (:fine-transaction-id existing)

                 transactions (if paid?
                                ;; Mark as paid: create income transactions
                                (let [base-amount (- (or amount 0.0) fine)
                                      base-tx (db.finance/add-transaction
                                               {:organization-id org-id
                                                :player-id player-id
                                                :amount base-amount
                                                :type "income"
                                                :category "monthly_fee"
                                                :description (str "Mensalidade " month "/" year)
                                                :payment-date payment-date
                                                :created-by user-id}
                                               tx)
                                      fine-tx (when (> fine 0)
                                                (db.finance/add-transaction
                                                 {:organization-id org-id
                                                  :player-id player-id
                                                  :amount fine
                                                  :type "income"
                                                  :category "fine"
                                                  :description (str "Multa Mensalidade " month "/" year)
                                                  :payment-date payment-date
                                                  :created-by user-id}
                                                 tx))]
                                  {:transaction-id (:id base-tx)
                                   :fine-transaction-id (:id fine-tx)})
                                ;; Mark as unpaid: reverse existing transactions if any
                                (do
                                  (when existing-tx-id
                                    (db.finance/reverse-transaction existing-tx-id tx))
                                  (when existing-fine-tx-id
                                    (db.finance/reverse-transaction existing-fine-tx-id tx))
                                  {:transaction-id nil
                                   :fine-transaction-id nil}))

                 payment (assoc payment-req
                                :organization-id org-id
                                :transaction-id (:transaction-id transactions)
                                :fine-transaction-id (:fine-transaction-id transactions))]
             (db.finance/mark-monthly-payment payment tx)
             (ok {:message "Payment status updated"
                  :transaction_id (:transaction-id transactions)
                  :fine_transaction_id (:fine-transaction-id transactions)}))))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-summary [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (let [summary (db.finance/get-summary org-id db)]
           (ok {:total_income (:total-income summary)
                :total_expense (:total-expense summary)
                :total_balance (:total-balance summary)})))
       (catch Exception e (exception/api-exception-handler e))))
