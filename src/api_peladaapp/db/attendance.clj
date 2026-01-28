(ns api-peladaapp.db.attendance
  (:require
   [api-peladaapp.helpers.misc :refer [unamespace]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count
  [result]
  (let [res (if (vector? result) (first result) result)]
    (-> res vals first)))

(s/defn upsert-attendance :- s/Int
  [pelada-id :- s/Int
   player-id :- s/Int
   status :- s/Str
   db]
  (-> (jdbc/execute! db ["INSERT INTO peladaattendance (pelada_id, player_id, status, updated_at)
                          VALUES (?, ?, ?, ?)
                          ON CONFLICT(pelada_id, player_id) DO UPDATE SET
                          status = excluded.status,
                          updated_at = excluded.updated_at"
                         pelada-id player-id status (str (java.time.Instant/now))])
      affected-rows-count))

(s/defn list-attendance-by-pelada :- [s/Any]
  [pelada-id :- s/Int
   db]
  (->> (sql/query db ["select * from peladaattendance where pelada_id = ?" pelada-id])
       (map unamespace)))

(s/defn delete-attendance :- s/Int
  [pelada-id :- s/Int
   player-id :- s/Int
   db]
  (-> (sql/delete! db :peladaattendance {:pelada_id pelada-id :player_id player-id})
      affected-rows-count))
