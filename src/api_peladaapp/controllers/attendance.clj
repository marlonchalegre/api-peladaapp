(ns api-peladaapp.controllers.attendance
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.pelada :as db.pelada]
   [schema.core :as s]))

(s/defn update-attendance :- s/Int
  [pelada-id :- s/Int
   player-id :- s/Int
   status :- s/Str
   db]
  (db.attendance/upsert-attendance pelada-id player-id status db))

(s/defn close-attendance :- s/Any
  [pelada-id :- s/Int
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (if (= "attendance" (:status pelada))
      (do
        (db.pelada/update-pelada pelada-id {:status "open"} db)
        (db.pelada/get-pelada pelada-id db))
      (throw (ex-info "Pelada is not in attendance status" {:type :bad-request :message "Pelada is not in attendance status"})))))

(s/defn get-player-attendance :- (s/maybe s/Any)
  [pelada-id :- s/Int
   player-id :- s/Int
   db]
  (let [attendance (db.attendance/list-attendance-by-pelada pelada-id db)]
    (first (filter #(= player-id (:player_id %)) attendance))))
