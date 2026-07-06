(ns api-peladaapp.db.finance
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [api-peladaapp.db.monthly-substitution :as db.monthly-sub]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn get-organization-finance
  [org-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :OrganizationFinances)
                  (h/where [:= :organization_id org-id]))
        result (jdbc/execute-one! db (hsql/format query) hsql/opts)]
    (if result
      (adapter.finance/db->finance result)
      {:organization-id org-id
       :mensalista-price 0.0
       :diarista-price 0.0
       :monthly-fine-amount 0.0
       :monthly-cut-off-day 5
       :currency "BRL"})))

(s/defn upsert-organization-finance
  [org-id :- s/Uuid finance db]
  (let [row (assoc (adapter.finance/finance->db finance) :organization_id org-id)
        exists-query (-> (h/select 1)
                         (h/from :OrganizationFinances)
                         (h/where [:= :organization_id org-id]))
        exists? (jdbc/execute-one! db (hsql/format exists-query) hsql/opts)]
    (if exists?
      (let [query (-> (h/update :OrganizationFinances)
                      (h/set {:mensalista_price (:mensalista_price row)
                              :diarista_price (:diarista_price row)
                              :monthly_fine_amount (:monthly_fine_amount row)
                              :monthly_cut_off_day (:monthly_cut_off_day row)
                              :currency (:currency row)})
                      (h/where [:= :organization_id org-id]))]
        (jdbc/execute! db (hsql/format query) hsql/opts))
      (let [query (-> (h/insert-into :OrganizationFinances)
                      (h/values [row]))]
        (jdbc/execute! db (hsql/format query) hsql/opts)))
    1))

(s/defn add-transaction
  [transaction :- s/Any db]
  (let [row (adapter.finance/transaction->db transaction)
        row (cond-> row
              (:type row) (update :type (fn [v] [:cast v :transaction_type]))
              (:status row) (update :status (fn [v] [:cast v :transaction_status])))
        insert-query (-> (h/insert-into :Transactions)
                         (h/values [row])
                         (h/returning :id))
        result (jdbc/execute-one! db (hsql/format insert-query) hsql/opts)
        new-id (:id result)]
    (adapter.finance/db->transaction (jdbc/execute-one! db (hsql/format (-> (h/select :*) (h/from :Transactions) (h/where [:= :id new-id]))) hsql/opts))))

(s/defn reverse-transaction
  [transaction-id :- s/Uuid db]
  (jdbc/with-transaction [tx db]
    (let [q1 (-> (h/update :Transactions) (h/set {:status [:cast "reversed" :transaction_status]}) (h/where [:= :id transaction-id]))
          q2 (-> (h/update :MonthlyPayments) (h/set {:paid false :transaction_id nil}) (h/where [:= :transaction_id transaction-id]))
          q3 (-> (h/update :MonthlyPayments) (h/set {:fine_transaction_id nil}) (h/where [:= :fine_transaction_id transaction-id]))]
      (jdbc/execute! tx (hsql/format q1))
      ;; If it's a base fee transaction, mark the payment as unpaid
      (jdbc/execute! tx (hsql/format q2))
      ;; If it's a fine transaction, just clear the link
      (jdbc/execute! tx (hsql/format q3)))))

(s/defn count-transactions
  [org-id :- s/Uuid db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Transactions)
                  (h/where [:= :organization_id org-id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        :count
        int)))

(s/defn list-transactions
  [org-id :- s/Uuid limit offset db]
  (let [query (-> (h/select :t.* [:u.name :player_name] [:uc.name :creator_name] [:p.scheduled_at :pelada_date])
                  (h/from [:Transactions :t])
                  (h/left-join [:OrganizationPlayers :op] [:= :t.player_id :op.id])
                  (h/left-join [:Users :u] [:= :op.user_id :u.id])
                  (h/left-join [:Users :uc] [:= :t.created_by :uc.id])
                  (h/left-join [:Peladas :p] [:= :t.pelada_id :p.id])
                  (h/where [:= :t.organization_id org-id])
                  (h/order-by [:t.payment_date :desc] [:t.created_at :desc])
                  (h/limit limit)
                  (h/offset offset))
        result (jdbc/execute! db (hsql/format query) hsql/opts)]
    (map adapter.finance/db->transaction result)))

(defn- substitution-active-in-month? [sub first-day last-day]
  (let [start-date (str (:start_date sub))
        end-date (some-> (:end_date sub) str)]
    (and (<= (compare start-date last-day) 0)
         (or (nil? (:end_date sub))
             (>= (compare end-date first-day) 0)))))

(defn- get-effective-member-type [player-id current-member-type active-subs-in-month]
  (let [player-id (misc/as-uuid player-id)
        temp-sub (first (filter (fn [sub] (= (misc/as-uuid (:temporary_player_id sub)) player-id))
                                active-subs-in-month))
        perm-sub (first (filter (fn [sub] (= (misc/as-uuid (:permanent_player_id sub)) player-id))
                                active-subs-in-month))]
    (cond
      temp-sub "mensalista_temporario"
      perm-sub "diarista_temporario"
      :else (cond
              (= current-member-type "mensalista_temporario") "diarista"
              (= current-member-type "diarista_temporario") "mensalista"
              :else current-member-type))))

(s/defn get-monthly-payments
  [org-id :- s/Uuid year month db]
  (let [ym (java.time.YearMonth/of year month)
        first-day (str (.atDay ym 1))
        last-day (str (.atEndOfMonth ym))
        subs (db.monthly-sub/list-substitutions-by-org org-id db)
        active-subs (filter (fn [sub] (substitution-active-in-month? sub first-day last-day)) subs)
        candidate-query (-> (h/select :op.id)
                            (h/from [:OrganizationPlayers :op])
                            (h/where [:and [:= :op.organization_id org-id]
                                      [:or [:in :op.member_type [[:cast "mensalista" :member_type]
                                                                 [:cast "mensalista_temporario" :member_type]
                                                                 [:cast "diarista_temporario" :member_type]]]
                                       [:exists (-> (h/select 1)
                                                    (h/from [:MonthlyPlayerSubstitutions :ms])
                                                    (h/where [:and [:= :ms.organization_id org-id]
                                                              [:or [:= :ms.permanent_player_id :op.id]
                                                               [:= :ms.temporary_player_id :op.id]]
                                                              [:<= :ms.start_date [[:cast last-day :date]]]
                                                              [:or [:is :ms.end_date nil]
                                                               [:>= :ms.end_date [[:cast first-day :date]]]]]))]]]))
        query (-> (h/select :mp.id [:op.id :player_id] [:u.name :player_name] :op.member_type
                            :mp.year :mp.month :mp.transaction_id :mp.fine_transaction_id :mp.paid
                            :t.amount :t.fine_amount
                            [:ft.amount :actual_fine_amount] [:ft.status :fine_status])
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/left-join [:MonthlyPayments :mp] [:and [:= :op.id :mp.player_id] [:= :mp.year year] [:= :mp.month month]])
                  (h/left-join [:Transactions :t] [:= :mp.transaction_id :t.id])
                  (h/left-join [:Transactions :ft] [:= :mp.fine_transaction_id :ft.id])
                  (h/where [:and [:= :op.organization_id org-id] [:in :op.id candidate-query]])
                  (h/order-by :u.name))
        result (jdbc/execute! db (hsql/format query) hsql/opts)
        mapped-payments (->> result
                             (map (fn [row]
                                    (let [player-id (:player_id row)
                                          current-type (:member_type row)
                                          effective-type (get-effective-member-type player-id current-type active-subs)]
                                      (assoc row :member_type effective-type))))
                             (filter (fn [row]
                                       (let [t (:member_type row)]
                                         (or (= t "mensalista")
                                             (= t "mensalista_temporario"))))))]
    (map adapter.finance/db->monthly-payment mapped-payments)))

(s/defn mark-monthly-payment
  [payment :- s/Any db]
  (let [row (adapter.finance/monthly-payment->db payment)
        exists-query (-> (h/select :id)
                         (h/from :MonthlyPayments)
                         (h/where [:and [:= :organization_id (:organization_id row)] [:= :player_id (:player_id row)] [:= :year (:year row)] [:= :month (:month row)]]))
        exists? (jdbc/execute-one! db (hsql/format exists-query) hsql/opts)]
    (if exists?
      (let [q (-> (h/update :MonthlyPayments)
                  (h/set {:transaction_id (:transaction_id row) :fine_transaction_id (:fine_transaction_id row) :paid (:paid row)})
                  (h/where [:= :id (:id exists?)]))]
        (jdbc/execute! db (hsql/format q)))
      (let [q (-> (h/insert-into :MonthlyPayments) (h/values [row]))]
        (jdbc/execute! db (hsql/format q))))
    1))

(s/defn get-summary
  [org-id :- s/Uuid db]
  (let [income-query (-> (h/select [[:sum :amount] :total])
                         (h/from :Transactions)
                         (h/where [:and [:= :organization_id org-id] [:= :type [:cast "income" :transaction_type]] [:= :status [:cast "paid" :transaction_status]]]))
        expense-query (-> (h/select [[:sum :amount] :total])
                          (h/from :Transactions)
                          (h/where [:and [:= :organization_id org-id] [:= :type [:cast "expense" :transaction_type]] [:= :status [:cast "paid" :transaction_status]]]))
        income-res (jdbc/execute-one! db (hsql/format income-query) hsql/opts)
        expense-res (jdbc/execute-one! db (hsql/format expense-query) hsql/opts)
        income (or (:total income-res) 0.0)
        expense (or (:total expense-res) 0.0)]
    {:total-income (double income)
     :total-expense (double expense)
     :total-balance (double (- income expense))}))
