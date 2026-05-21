(ns api-peladaapp.controllers.finance
  (:require
   [api-peladaapp.db.finance :as db.finance]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.logic.finance :as logic.finance]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn get-finance
  [org-id :- s/Uuid db]
  (db.finance/get-organization-finance org-id db))

(s/defn update-finance
  [org-id :- s/Uuid finance db]
  (db.finance/upsert-organization-finance org-id finance db))

(s/defn list-transactions
  [org-id :- s/Uuid limit :- s/Int offset :- s/Int db]
  (db.finance/list-transactions org-id limit offset db))

(s/defn count-transactions
  [org-id :- s/Uuid db]
  (db.finance/count-transactions org-id db))

(s/defn add-transaction
  [transaction db]
  (db.finance/add-transaction transaction db))

(s/defn reverse-transaction
  [tx-id :- s/Uuid db]
  (db.finance/reverse-transaction tx-id db))

(s/defn get-monthly-payments
  [org-id :- s/Uuid year :- s/Int month :- s/Int db]
  (db.finance/get-monthly-payments org-id year month db))

(s/defn get-summary
  [org-id :- s/Uuid db]
  (db.finance/get-summary org-id db))

(s/defn mark-monthly-payment
  [org-id :- s/Uuid user-id :- s/Uuid payment-req body db]
  (jdbc/with-transaction [tx db]
    (let [paid? (:paid payment-req)
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
          existing (first (filter (fn [p] (and (= (misc/as-uuid (:player-id p)) (misc/as-uuid player-id))
                                               (= (:year p) year)
                                               (= (:month p) month)))
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
      {:transaction-id (:transaction-id transactions)
       :fine-transaction-id (:fine-transaction-id transactions)})))
