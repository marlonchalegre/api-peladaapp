(ns api-peladaapp.db.attendance
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
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
  (jdbc/execute! db ["PRAGMA busy_timeout = 5000"])
  (-> (jdbc/execute! db ["INSERT INTO peladaattendance (pelada_id, player_id, status, updated_at)
                          VALUES (?, ?, ?, ?)
                          ON CONFLICT(pelada_id, player_id) DO UPDATE SET
                          status = excluded.status,
                          updated_at = excluded.updated_at"
                         pelada-id player-id status (str (java.time.Instant/now))])
      affected-rows-count))

(s/defn batch-upsert-attendance :- s/Int
  [pelada-id :- s/Int
   player-ids :- [s/Int]
   status :- s/Str
   db]
  (if (empty? player-ids)
    0
    (let [now (str (java.time.Instant/now))
          rows (map (fn [pid] [pelada-id pid status now]) player-ids)
          sql-str (str "INSERT INTO peladaattendance (pelada_id, player_id, status, updated_at) VALUES "
                       (str/join ", " (repeat (count rows) "(?, ?, ?, ?)"))
                       " ON CONFLICT(pelada_id, player_id) DO UPDATE SET "
                       "status = excluded.status, "
                       "updated_at = excluded.updated_at")]
      (jdbc/with-transaction [tx db]
        (jdbc/execute! tx ["PRAGMA busy_timeout = 5000"])
        (-> (jdbc/execute! tx (into [sql-str] (apply concat rows)))
            affected-rows-count)))))

(s/defn list-attendance-by-pelada :- [s/Any]
  [pelada-id :- s/Int
   db]
  (jdbc/execute! db ["select * from peladaattendance where pelada_id = ?" pelada-id]
                 {:builder-fn rs/as-unqualified-lower-maps}))

(s/defn list-pending-attendance-by-pelada [pelada-id db]
  (let [query "SELECT op.id as player_id, u.name as player_name
               FROM OrganizationPlayers op
               JOIN Users u ON op.user_id = u.id
               JOIN Peladas p ON op.organization_id = p.organization_id
               WHERE p.id = ?
               AND NOT EXISTS (
                 SELECT 1 FROM PeladaAttendance pa 
                 WHERE pa.pelada_id = p.id AND pa.player_id = op.id
               )"
        results (jdbc/execute! db [query pelada-id] {:builder-fn rs/as-unqualified-lower-maps})]
    (map (fn [r] {:player-id (:player_id r) :player-name (:player_name r)}) results)))

(s/defn delete-attendance :- s/Int
  [pelada-id :- s/Int
   player-id :- s/Int
   db]
  (-> (sql/delete! db :peladaattendance {:pelada_id pelada-id :player_id player-id})
      affected-rows-count))
