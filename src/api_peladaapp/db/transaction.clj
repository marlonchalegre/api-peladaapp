(ns api-peladaapp.db.transaction
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn list-transactions-by-pelada
  [pelada-id db]
  (let [query "SELECT t.*, u.name as player_name 
               FROM \"Transactions\" t
               LEFT JOIN \"OrganizationPlayers\" op ON t.player_id = op.id
               LEFT JOIN \"Users\" u ON op.user_id = u.id
               WHERE t.pelada_id = ?
               ORDER BY t.created_at DESC"
        result (jdbc/execute! db [query pelada-id])]
    (map adapter.finance/db->transaction result)))
