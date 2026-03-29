(ns api-peladaapp.handlers.finance
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [api-peladaapp.db.finance :as db.finance]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :refer [created ok]]
   [api-peladaapp.logic.authorization :as auth]
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
         (db.finance/upsert-organization-finance org-id (adapter.finance/db->finance body) db)
         (ok {:message "Finance settings updated"}))
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
         (let [transaction (assoc body
                                  :organization-id org-id
                                  :created-by user-id)
               db-transaction (adapter.finance/transaction->db transaction)
               stored-tx (db.finance/add-transaction db-transaction db)
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
           (let [payment-req (adapter.finance/db->monthly-payment body)
                 paid? (:paid payment-req)
                 player-id (:player-id payment-req)
                 year (:year payment-req)
                 month (:month payment-req)
                 amount (:amount body)
                 payment-date (:payment-date body)

                 ;; Find existing payment record to see if there's a transaction to reverse
                 existing-payments (db.finance/get-monthly-payments org-id year month tx)
                 _ (println "DEBUG MARK-MONTHLY: existing-payments count=" (count existing-payments))
                 existing (first (filter (fn [p] (and (= (:player-id p) player-id)
                                                      (= (:year p) year)
                                                      (= (:month p) month)))
                                         existing-payments))
                 _ (println "DEBUG MARK-MONTHLY: existing=" (pr-str existing))

                 existing-tx-id (:transaction-id existing)
                 _ (println "DEBUG MARK-MONTHLY: existing-tx-id=" existing-tx-id)

                 transaction-id (if paid?
                                  ;; Mark as paid: create income transaction
                                  (let [t (db.finance/add-transaction
                                           {:organization-id org-id
                                            :player-id player-id
                                            :amount (or amount 0.0)
                                            :type "income"
                                            :category "monthly_fee"
                                            :description (str "Mensalidade " month "/" year)
                                            :payment_date (or payment-date (str (java.time.LocalDate/now)))
                                            :created-by user-id}
                                           tx)]
                                    (:id t))
                                  ;; Mark as unpaid: reverse existing transaction if any
                                  (do
                                    (when existing-tx-id
                                      (println "DEBUG MARK-MONTHLY: reversing tx" existing-tx-id)
                                      (db.finance/reverse-transaction existing-tx-id tx))
                                    nil))

                 payment (assoc payment-req :organization-id org-id :transaction-id transaction-id)]
             (db.finance/mark-monthly-payment payment tx)
             (ok {:message "Payment status updated" :transaction_id transaction-id}))))
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
