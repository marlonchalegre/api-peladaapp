(ns api-peladaapp.db.transaction
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn list-transactions-by-pelada
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :t.* [:u.name :player_name])
                  (h/from [:Transactions :t])
                  (h/left-join [:OrganizationPlayers :op] [:= :t.player_id :op.id])
                  (h/left-join [:Users :u] [:= :op.user_id :u.id])
                  (h/where [:= :t.pelada_id pelada-id])
                  (h/order-by [:t.created_at :desc]))
        result (jdbc/execute! db (hsql/format query) hsql/opts)]
    (map adapter.finance/db->transaction result)))
