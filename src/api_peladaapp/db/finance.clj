(ns api-peladaapp.db.finance
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn get-organization-finance
  [org-id db]
  (let [query (-> (h/select :*)
                  (h/from :OrganizationFinances)
                  (h/where [:= :organization_id org-id]))
        result (jdbc/execute-one! db (hsql/format query) opts)]
    (if result
      (adapter.finance/db->finance result)
      {:organization-id org-id
       :mensalista-price 0.0
       :diarista-price 0.0
       :monthly-fine-amount 0.0
       :monthly-cut-off-day 5
       :currency "BRL"})))

(s/defn upsert-organization-finance
  [org-id finance db]
  (let [row (assoc (adapter.finance/finance->db finance) :organization_id org-id)
        exists-query (-> (h/select 1)
                         (h/from :OrganizationFinances)
                         (h/where [:= :organization_id org-id]))
        exists? (jdbc/execute-one! db (hsql/format exists-query))]
    (if exists?
      (let [query (-> (h/update :OrganizationFinances)
                      (h/set {:mensalista_price (:mensalista_price row)
                              :diarista_price (:diarista_price row)
                              :monthly_fine_amount (:monthly_fine_amount row)
                              :monthly_cut_off_day (:monthly_cut_off_day row)
                              :currency (:currency row)})
                      (h/where [:= :organization_id org-id]))]
        (jdbc/execute! db (hsql/format query)))
      (let [query (-> (h/insert-into :OrganizationFinances)
                      (h/values [row]))]
        (jdbc/execute! db (hsql/format query))))
    1))

(s/defn add-transaction
  [transaction db]
  (let [row (adapter.finance/transaction->db transaction)
        insert-query (-> (h/insert-into :Transactions)
                         (h/values [row])
                         (h/returning :id))
        result (jdbc/execute-one! db (hsql/format insert-query) opts)
        new-id (:id result)]
    (adapter.finance/db->transaction (jdbc/execute-one! db (hsql/format (-> (h/select :*) (h/from :Transactions) (h/where [:= :id new-id]))) opts))))

(s/defn reverse-transaction
  [transaction-id db]
  (jdbc/with-transaction [tx db]
    (let [q1 (-> (h/update :Transactions) (h/set {:status "reversed"}) (h/where [:= :id transaction-id]))
          q2 (-> (h/update :MonthlyPayments) (h/set {:paid false :transaction_id nil}) (h/where [:= :transaction_id transaction-id]))
          q3 (-> (h/update :MonthlyPayments) (h/set {:fine_transaction_id nil}) (h/where [:= :fine_transaction_id transaction-id]))]
      (jdbc/execute! tx (hsql/format q1))
      ;; If it's a base fee transaction, mark the payment as unpaid
      (jdbc/execute! tx (hsql/format q2))
      ;; If it's a fine transaction, just clear the link
      (jdbc/execute! tx (hsql/format q3)))))

(s/defn count-transactions
  [org-id db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Transactions)
                  (h/where [:= :organization_id org-id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        :count
        int)))

(s/defn list-transactions
  [org-id limit offset db]
  (let [query (-> (h/select :t.* [:u.name :player_name] [:uc.name :creator_name])
                  (h/from [:Transactions :t])
                  (h/left-join [:OrganizationPlayers :op] [:= :t.player_id :op.id])
                  (h/left-join [:Users :u] [:= :op.user_id :u.id])
                  (h/left-join [:Users :uc] [:= :t.created_by :uc.id])
                  (h/where [:= :t.organization_id org-id])
                  (h/order-by [:t.payment_date :desc] [:t.created_at :desc])
                  (h/limit limit)
                  (h/offset offset))
        result (jdbc/execute! db (hsql/format query) opts)]
    (map adapter.finance/db->transaction result)))

(s/defn get-monthly-payments
  [org-id year month db]
  (let [query (-> (h/select :mp.id [:op.id :player_id] [:u.name :player_name] :op.member_type
                            :mp.year :mp.month :mp.transaction_id :mp.fine_transaction_id :mp.paid
                            :t.amount :t.fine_amount
                            [:ft.amount :actual_fine_amount] [:ft.status :fine_status])
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/left-join [:MonthlyPayments :mp] [:and [:= :op.id :mp.player_id] [:= :mp.year year] [:= :mp.month month]])
                  (h/left-join [:Transactions :t] [:= :mp.transaction_id :t.id])
                  (h/left-join [:Transactions :ft] [:= :mp.fine_transaction_id :ft.id])
                  (h/where [:and [:= :op.organization_id org-id] [:in :op.member_type ["mensalista" "mensalista_temporario"]]])
                  (h/order-by :u.name))
        result (jdbc/execute! db (hsql/format query) opts)]
    (map adapter.finance/db->monthly-payment result)))

(s/defn mark-monthly-payment
  [payment db]
  (let [row (adapter.finance/monthly-payment->db payment)
        exists-query (-> (h/select :id)
                         (h/from :MonthlyPayments)
                         (h/where [:and [:= :organization_id (:organization_id row)] [:= :player_id (:player_id row)] [:= :year (:year row)] [:= :month (:month row)]]))
        exists? (jdbc/execute-one! db (hsql/format exists-query) opts)]
    (if exists?
      (let [q (-> (h/update :MonthlyPayments)
                  (h/set {:transaction_id (:transaction_id row) :fine_transaction_id (:fine_transaction_id row) :paid (:paid row)})
                  (h/where [:= :id (:id exists?)]))]
        (jdbc/execute! db (hsql/format q)))
      (let [q (-> (h/insert-into :MonthlyPayments) (h/values [row]))]
        (jdbc/execute! db (hsql/format q))))
    1))

(s/defn get-summary
  [org-id db]
  (let [income-query (-> (h/select [[:sum :amount] :total])
                         (h/from :Transactions)
                         (h/where [:and [:= :organization_id org-id] [:= :type "income"] [:= :status "paid"]]))
        expense-query (-> (h/select [[:sum :amount] :total])
                          (h/from :Transactions)
                          (h/where [:and [:= :organization_id org-id] [:= :type "expense"] [:= :status "paid"]]))
        income-res (jdbc/execute-one! db (hsql/format income-query) opts)
        expense-res (jdbc/execute-one! db (hsql/format expense-query) opts)
        income (or (:total income-res) 0.0)
        expense (or (:total expense-res) 0.0)]
    {:total-income (double income)
     :total-expense (double expense)
     :total-balance (double (- income expense))}))
