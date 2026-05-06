(ns api-peladaapp.db.finance
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn get-organization-finance
  [org-id db]
  (let [query "SELECT * FROM \"OrganizationFinances\" WHERE \"organization_id\" = ?"
        result (jdbc/execute-one! db [query org-id])]
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
  (let [row (adapter.finance/finance->db finance)
        exists? (jdbc/execute-one! db ["SELECT 1 FROM \"OrganizationFinances\" WHERE \"organization_id\" = ?" org-id])]
    (if exists?
      (jdbc/execute! db ["UPDATE \"OrganizationFinances\" SET \"mensalista_price\" = ?, \"diarista_price\" = ?, \"monthly_fine_amount\" = ?, \"monthly_cut_off_day\" = ?, \"currency\" = ? WHERE \"organization_id\" = ?"
                         (:mensalista_price row) (:diarista_price row) (:monthly_fine_amount row) (:monthly_cut_off_day row) (:currency row) org-id])
      (jdbc/execute! db ["INSERT INTO \"OrganizationFinances\" (\"organization_id\", \"mensalista_price\", \"diarista_price\", \"monthly_fine_amount\", \"monthly_cut_off_day\", \"currency\") VALUES (?, ?, ?, ?, ?, ?)"
                         org-id (:mensalista_price row) (:diarista_price row) (:monthly_fine_amount row) (:monthly_cut_off_day row) (:currency row)]))
    1))

(s/defn add-transaction
  [transaction db]
  (let [row (adapter.finance/transaction->db transaction)
        sql "INSERT INTO \"Transactions\" (\"organization_id\", \"player_id\", \"pelada_id\", \"amount\", \"fine_amount\", \"type\", \"category\", \"description\", \"payment_date\", \"created_by\", \"status\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        params [(:organization_id row) (:player_id row) (:pelada_id row) (:amount row) (or (:fine_amount row) 0.0) (:type row) (:category row) (:description row) (:payment_date row) (:created_by row) (or (:status row) "paid")]
        _ (jdbc/execute! db (into [sql] params))
        result (jdbc/execute-one! db ["SELECT last_insert_rowid() as id"])
        new-id (or (:id result) (get result (keyword "last_insert_rowid()")) (get result "last_insert_rowid()"))]
    (adapter.finance/db->transaction (jdbc/execute-one! db ["SELECT * FROM \"Transactions\" WHERE id = ?" new-id]))))

(s/defn reverse-transaction
  [transaction-id db]
  (jdbc/with-transaction [tx db]
    (jdbc/execute! tx ["UPDATE \"Transactions\" SET \"status\" = 'reversed' WHERE id = ?" transaction-id])
    (jdbc/execute! tx ["UPDATE \"MonthlyPayments\" SET \"paid\" = 0, \"transaction_id\" = NULL WHERE \"transaction_id\" = ?" transaction-id])))

(s/defn count-transactions
  [org-id db]
  (:count (jdbc/execute-one! db ["SELECT COUNT(*) as count FROM \"Transactions\" WHERE \"organization_id\" = ?" org-id])))

(s/defn list-transactions
  [org-id limit offset db]
  (let [query "SELECT t.*, u.name as player_name, uc.name as creator_name
               FROM \"Transactions\" t
               LEFT JOIN OrganizationPlayers op ON t.player_id = op.id
               LEFT JOIN Users u ON op.user_id = u.id
               LEFT JOIN Users uc ON t.created_by = uc.id
               WHERE t.\"organization_id\" = ?
               ORDER BY t.\"payment_date\" DESC, t.\"created_at\" DESC
               LIMIT ? OFFSET ?"
        result (jdbc/execute! db [query org-id limit offset])]
    (map adapter.finance/db->transaction result)))

(s/defn get-monthly-payments
  [org-id year month db]
  (let [query "SELECT mp.id, op.id as player_id, u.name as player_name, op.member_type, 
                      mp.year, mp.month, mp.transaction_id, mp.paid
               FROM OrganizationPlayers op
               JOIN Users u ON op.user_id = u.id
               LEFT JOIN \"MonthlyPayments\" mp ON op.id = mp.player_id 
                    AND mp.year = ? AND mp.month = ?
               WHERE op.organization_id = ? AND op.member_type = 'mensalista'
               ORDER BY u.name ASC"
        result (jdbc/execute! db [query year month org-id])]
    (map adapter.finance/db->monthly-payment result)))

(s/defn mark-monthly-payment
  [payment db]
  (let [row (adapter.finance/monthly-payment->db payment)
        exists? (jdbc/execute-one! db ["SELECT id FROM \"MonthlyPayments\" WHERE \"organization_id\" = ? AND \"player_id\" = ? AND \"year\" = ? AND \"month\" = ?"
                                       (:organization_id row) (:player_id row) (:year row) (:month row)])]
    (if exists?
      (jdbc/execute! db ["UPDATE \"MonthlyPayments\" SET \"transaction_id\" = ?, \"paid\" = ? WHERE id = ?"
                         (:transaction_id row) (:paid row) (or (:id exists?) (get exists? (keyword "MonthlyPayments/id")))])
      (jdbc/execute! db ["INSERT INTO \"MonthlyPayments\" (\"organization_id\", \"player_id\", \"year\", \"month\", \"transaction_id\", \"paid\") VALUES (?, ?, ?, ?, ?, ?)"
                         (:organization_id row) (:player_id row) (:year row) (:month row) (:transaction_id row) (:paid row)]))
    1))

(s/defn get-summary
  [org-id db]
  (let [income-query "SELECT SUM(amount) as total FROM \"Transactions\" WHERE \"organization_id\" = ? AND \"type\" = 'income' AND \"status\" = 'paid'"
        expense-query "SELECT SUM(amount) as total FROM \"Transactions\" WHERE \"organization_id\" = ? AND \"type\" = 'expense' AND \"status\" = 'paid'"
        income-res (jdbc/execute-one! db [income-query org-id])
        expense-res (jdbc/execute-one! db [expense-query org-id])
        income (or (:total income-res) (get income-res (keyword "SUM(amount)")) (get income-res "SUM(amount)") 0.0)
        expense (or (:total expense-res) (get expense-res (keyword "SUM(amount)")) (get expense-res "SUM(amount)") 0.0)]
    {:total-income (double (or income 0.0))
     :total-expense (double (or expense 0.0))
     :total-balance (double (- (or income 0.0) (or expense 0.0)))}))
