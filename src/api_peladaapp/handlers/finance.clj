(ns api-peladaapp.handlers.finance
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [api-peladaapp.controllers.finance :as controller.finance]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :refer [created ok]]
   [api-peladaapp.logic.authorization :as auth]))

(defn get-finance [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :finance_control db)
         (ok (adapter.finance/model->finance-response (controller.finance/get-finance org-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-finance [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :finance_control db)
         (let [model (adapter.finance/payload->finance body)]
           (controller.finance/update-finance org-id model db)
           (ok {:message "Finance settings updated"})))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-transactions [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             {:keys [page per-page]} (pagination/parse-pagination-params (:query-params request))
             offset (* (dec page) per-page)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :finance_control db)
         (let [txs (controller.finance/list-transactions org-id per-page offset db)
               total-count (controller.finance/count-transactions org-id db)
               pagination-headers (:headers (pagination/with-pagination-headers nil total-count page per-page))]
           (ok (map adapter.finance/model->transaction-response txs) pagination-headers)))
       (catch Exception e (exception/api-exception-handler e))))

(defn add-transaction [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :finance_control db)
         (let [transaction (assoc (adapter.finance/payload->transaction body)
                                  :organization-id org-id
                                  :created-by user-id)
               model-tx (controller.finance/add-transaction transaction db)]
           (created (adapter.finance/model->transaction-response model-tx))))
       (catch Exception e (exception/api-exception-handler e))))

(defn reverse-transaction [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             tx-id (misc/as-uuid (get-in request [:params :tx_id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :finance_control db)
         (controller.finance/reverse-transaction tx-id db)
         (ok {:message "Transaction reversed"}))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-monthly-payments [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             year (Integer/parseInt (get-in request [:query-params "year"]))
             month (Integer/parseInt (get-in request [:query-params "month"]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :finance_control db)
         (ok (map adapter.finance/model->monthly-payment-response (controller.finance/get-monthly-payments org-id year month db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn mark-monthly-payment [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :finance_control db)
         (let [payment-req (adapter.finance/payload->monthly-payment body)
               res (controller.finance/mark-monthly-payment org-id user-id payment-req body db)]
           (ok {:message "Payment status updated"
                :transaction_id (:transaction-id res)
                :fine_transaction_id (:fine-transaction-id res)})))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-summary [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :finance_control db)
         (let [summary (controller.finance/get-summary org-id db)]
           (ok {:total_income (:total-income summary)
                :total_expense (:total-expense summary)
                :total_balance (:total-balance summary)})))
       (catch Exception e (exception/api-exception-handler e))))
