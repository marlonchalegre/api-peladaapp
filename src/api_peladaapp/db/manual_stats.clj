(ns api-peladaapp.db.manual-stats
  (:require
   [api-peladaapp.adapters.manual-stats :as adapter.manual-stats]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn upsert-manual-stats :- s/Int
  [{:keys [organization-id player-id year goals assists own-goals]}
   db]
  (let [query (-> (h/insert-into :ManualStats)
                  (h/values [{:organization_id organization-id
                              :player_id player-id
                              :year year
                              :goals (or goals 0)
                              :assists (or assists 0)
                              :own_goals (or own-goals 0)}])
                  (h/on-conflict :organization_id :player_id :year)
                  (h/do-update-set
                   {:goals [:+ [[:coalesce :ManualStats.goals 0]] :excluded.goals]
                    :assists [:+ [[:coalesce :ManualStats.assists 0]] :excluded.assists]
                    :own_goals [:+ [[:coalesce :ManualStats.own_goals 0]] :excluded.own_goals]}))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn delete-manual-stats :- s/Int
  [organization-id player-id year db]
  (let [query (-> (h/delete-from :ManualStats)
                  (h/where [:= :organization_id organization-id]
                           [:= :player_id player-id]
                           [:= :year year]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn list-manual-stats-by-org-and-year
  [organization-id year db]
  (let [query (-> (h/select :*)
                  (h/from :ManualStats)
                  (h/where [:= :organization_id organization-id]
                           [:= :year year]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.manual-stats/db->model))))
