(ns api-peladaapp.db.attendance
  (:require
   [api-peladaapp.helpers.misc :refer [unamespace]]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count
  [result]
  (-> result vals first))

(s/defn upsert-attendance :- s/Int
  [pelada-id :- s/Int
   player-id :- s/Int
   status :- s/Str
   db]
  (let [existing (sql/query db ["select id from peladaattendance where pelada_id = ? and player_id = ?" pelada-id player-id])]
    (if (seq existing)
      (-> (sql/update! db :peladaattendance {:status status :updated_at (str (java.time.Instant/now))} {:id (:id (unamespace (first existing)))})
          affected-rows-count)
      (-> (sql/insert! db :peladaattendance {:pelada_id pelada-id :player_id player-id :status status})
          affected-rows-count))))

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
