(ns api-peladaapp.controllers.attendance
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.vote :as db.vote]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn update-attendance :- s/Int
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   status :- s/Str
   db]
  (db.attendance/upsert-attendance pelada-id player-id status db))

(s/defn batch-update-attendance :- s/Int
  [pelada-id :- s/Uuid
   player-ids :- [s/Uuid]
   status :- s/Str
   db]
  (db.attendance/batch-upsert-attendance pelada-id player-ids status db))

(s/defn close-attendance :- s/Any

  [pelada-id :- s/Uuid
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (if (= "attendance" (:status pelada))
      (do
        (db.pelada/update-pelada pelada-id {:status "open"} db)
        (db.pelada/get-pelada pelada-id db))
      (throw (ex-info "Pelada is not in attendance status" {:type :bad-request :message "Pelada is not in attendance status"})))))

(s/defn get-player-attendance :- (s/maybe s/Any)
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [attendance (db.attendance/list-attendance-by-pelada pelada-id db)]
    (first (filter #(= player-id (:player_id %)) attendance))))

(s/defn update-voting-enabled :- s/Any
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   enabled? :- s/Bool
   db]
  (jdbc/with-transaction [tx db]
    (let [res (db.attendance/update-voting-enabled pelada-id player-id enabled? tx)]
      (when-not enabled?
        (db.vote/delete-votes-for-target pelada-id player-id tx))
      {:updated res})))
