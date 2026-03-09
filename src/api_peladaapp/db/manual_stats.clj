(ns api-peladaapp.db.manual-stats
  (:require
   [api-peladaapp.adapters.manual-stats :as adapter.manual-stats]
   [next.jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn upsert-manual-stats :- s/Int
  [{:keys [organization-id player-id year goals assists own-goals]}
   db]
  (let [sql "INSERT INTO ManualStats (organization_id, player_id, year, goals, assists, own_goals)
             VALUES (?, ?, ?, ?, ?, ?)
             ON CONFLICT(organization_id, player_id, year) DO UPDATE SET
               goals = COALESCE(ManualStats.goals, 0) + excluded.goals,
               assists = COALESCE(ManualStats.assists, 0) + excluded.assists,
               own_goals = COALESCE(ManualStats.own_goals, 0) + excluded.own_goals"]
    (-> (next.jdbc/execute-one! db [sql organization-id player-id year (or goals 0) (or assists 0) (or own-goals 0)] opts)
        affected-rows-count)))

(s/defn delete-manual-stats :- s/Int
  [organization-id player-id year db]
  (-> (sql/delete! db :ManualStats {:organization_id organization-id
                                    :player_id player-id
                                    :year year})
      affected-rows-count))

(s/defn list-manual-stats-by-org-and-year
  [organization-id year db]
  (->> (sql/find-by-keys db :ManualStats {:organization_id organization-id :year year} opts)
       (map adapter.manual-stats/db->model)))
