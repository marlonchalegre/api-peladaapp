(ns api-peladaapp.db.monthly-substitution
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn create-substitution!
  [substitution db]
  (let [row (cond-> substitution
              (:start_date substitution) (assoc :start_date [[:cast (:start_date substitution) :date]])
              (:end_date substitution)   (assoc :end_date [[:cast (:end_date substitution) :date]]))
        query (-> (h/insert-into :MonthlyPlayerSubstitutions)
                  (h/values [row])
                  (h/returning :id))
        res (jdbc/execute-one! db (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})]
    (:id res)))

(s/defn get-active-substitution-by-permanent-player
  [player-id db]
  (let [query (-> (h/select :*)
                  (h/from :MonthlyPlayerSubstitutions)
                  (h/where [:and [:= :permanent_player_id player-id] [:= :active true]]))]
    (jdbc/execute-one! db (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})))

(s/defn get-active-substitution-by-temporary-player
  [player-id db]
  (let [query (-> (h/select :*)
                  (h/from :MonthlyPlayerSubstitutions)
                  (h/where [:and [:= :temporary_player_id player-id] [:= :active true]]))]
    (jdbc/execute-one! db (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})))

(s/defn list-substitutions-by-org
  [org-id db]
  (let [query (-> (h/select :ms.* [:up.name :permanent_player_name] [:ut.name :temporary_player_name])
                  (h/from [:MonthlyPlayerSubstitutions :ms])
                  (h/join [:OrganizationPlayers :op] [:= :ms.permanent_player_id :op.id])
                  (h/join [:Users :up] [:= :op.user_id :up.id])
                  (h/join [:OrganizationPlayers :ot] [:= :ms.temporary_player_id :ot.id])
                  (h/join [:Users :ut] [:= :ot.user_id :ut.id])
                  (h/where [:= :ms.organization_id org-id])
                  (h/order-by [:ms.created_at :desc]))]
    (jdbc/execute! db (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})))

(s/defn end-substitution!
  [sub-id end-date db]
  (let [query (-> (h/update :MonthlyPlayerSubstitutions)
                  (h/set {:active false :end_date [[:cast end-date :date]]})
                  (h/where [:= :id sub-id]))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn get-substitution-by-id
  [sub-id db]
  (let [query (-> (h/select :*)
                  (h/from :MonthlyPlayerSubstitutions)
                  (h/where [:= :id sub-id]))]
    (jdbc/execute-one! db (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})))
