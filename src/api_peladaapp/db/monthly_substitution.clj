(ns api-peladaapp.db.monthly-substitution
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn create-substitution! :- s/Uuid
  [substitution :- {:organization_id s/Uuid
                    :permanent_player_id s/Uuid
                    :temporary_player_id s/Uuid
                    (s/optional-key :start_date) s/Str
                    (s/optional-key :end_date) s/Str
                    :active s/Bool}
   db]
  (let [row (cond-> substitution
              (:start_date substitution) (assoc :start_date [[:cast (:start_date substitution) :date]])
              (:end_date substitution)   (assoc :end_date [[:cast (:end_date substitution) :date]]))
        query (-> (h/insert-into :MonthlyPlayerSubstitutions)
                  (h/values [row])
                  (h/returning :id))
        res (jdbc/execute-one! db (hsql/format query) hsql/opts)]
    (:id res)))

(s/defn get-active-substitution-by-permanent-player
  [player-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :MonthlyPlayerSubstitutions)
                  (h/where [:and [:= :permanent_player_id player-id] [:= :active true]]))]
    (jdbc/execute-one! db (hsql/format query) hsql/opts)))

(s/defn get-active-substitution-by-temporary-player
  [player-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :MonthlyPlayerSubstitutions)
                  (h/where [:and [:= :temporary_player_id player-id] [:= :active true]]))]
    (jdbc/execute-one! db (hsql/format query) hsql/opts)))

(s/defn list-substitutions-by-org
  [org-id :- s/Uuid db]
  (let [query (-> (h/select :ms.* [:up.name :permanent_player_name] [:ut.name :temporary_player_name])
                  (h/from [:MonthlyPlayerSubstitutions :ms])
                  (h/join [:OrganizationPlayers :op] [:= :ms.permanent_player_id :op.id])
                  (h/join [:Users :up] [:= :op.user_id :up.id])
                  (h/join [:OrganizationPlayers :ot] [:= :ms.temporary_player_id :ot.id])
                  (h/join [:Users :ut] [:= :ot.user_id :ut.id])
                  (h/where [:= :ms.organization_id org-id])
                  (h/order-by [:ms.created_at :desc]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn end-substitution! :- s/Uuid
  [sub-id :- s/Uuid end-date db]
  (let [query (-> (h/update :MonthlyPlayerSubstitutions)
                  (h/set {:active false :end_date [[:cast end-date :date]]})
                  (h/where [:= :id sub-id])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))

(s/defn get-substitution-by-id
  [sub-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :MonthlyPlayerSubstitutions)
                  (h/where [:= :id sub-id]))]
    (jdbc/execute-one! db (hsql/format query) hsql/opts)))
