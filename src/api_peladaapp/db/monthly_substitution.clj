(ns api-peladaapp.db.monthly-substitution
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count
  [result]
  (let [res (if (vector? result) (first result) result)]
    (-> res vals first)))

(s/defn create-substitution!
  [substitution db]
  (sql/insert! db :MonthlyPlayerSubstitutions substitution))

(s/defn get-active-substitution-by-permanent-player
  [player-id db]
  (jdbc/execute-one! db ["SELECT * FROM MonthlyPlayerSubstitutions WHERE permanent_player_id = ? AND active = 1" player-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(s/defn get-active-substitution-by-temporary-player
  [player-id db]
  (jdbc/execute-one! db ["SELECT * FROM MonthlyPlayerSubstitutions WHERE temporary_player_id = ? AND active = 1" player-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))

(s/defn list-substitutions-by-org
  [org-id db]
  (jdbc/execute! db ["SELECT ms.*, 
                            up.name as permanent_player_name, 
                            ut.name as temporary_player_name
                     FROM MonthlyPlayerSubstitutions ms
                     JOIN OrganizationPlayers op ON ms.permanent_player_id = op.id
                     JOIN Users up ON op.user_id = up.id
                     JOIN OrganizationPlayers ot ON ms.temporary_player_id = ot.id
                     JOIN Users ut ON ot.user_id = ut.id
                     WHERE ms.organization_id = ?
                     ORDER BY ms.created_at DESC" org-id]
                 {:builder-fn rs/as-unqualified-lower-maps}))

(s/defn end-substitution!
  [sub-id end-date db]
  (-> (sql/update! db :MonthlyPlayerSubstitutions
                   {:active 0 :end_date end-date}
                   {:id sub-id})
      affected-rows-count))

(s/defn get-substitution-by-id
  [sub-id db]
  (jdbc/execute-one! db ["SELECT * FROM MonthlyPlayerSubstitutions WHERE id = ?" sub-id]
                     {:builder-fn rs/as-unqualified-lower-maps}))
