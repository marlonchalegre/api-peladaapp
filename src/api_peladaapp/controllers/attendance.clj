(ns api-peladaapp.controllers.attendance
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.authorization :as auth]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn update-player-attendance :- s/Int
  [pelada-id :- s/Uuid
   user-id :- s/Uuid
   target-player-id :- (s/maybe s/Uuid)
   status :- s/Str
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        _ (when (nil? pelada)
            (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"})))
        org-id (:organization-id pelada)
        current-player (db.player/get-org-player-by-user-id user-id org-id db)]
    (if target-player-id
      ;; If target player id is provided, check if user is admin
      (auth/require-organization-admin! user-id org-id db)
      ;; Otherwise, update current user's attendance
      (when (nil? current-player)
        (throw (ex-info "User is not a player in this organization" {:type :forbidden :message "User is not a player in this organization"}))))
    (let [player-id (or target-player-id (:id current-player))
          target-player (if target-player-id
                          (db.player/get-player target-player-id db)
                          current-player)
          _ (when (nil? target-player)
              (throw (ex-info "Player not found" {:type :not-found :message "Player not found"})))
          final-status (if (and (= status "confirmed")
                                (not target-player-id)
                                (not= "mensalista" (:member-type target-player)))
                         "waitlist"
                         status)]
      (db.attendance/upsert-attendance pelada-id player-id final-status db))))

(s/defn update-attendance :- s/Int
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   status :- s/Str
   db]
  (db.attendance/upsert-attendance pelada-id player-id status db))

(s/defn batch-update-attendance :- s/Int
  [pelada-id :- s/Uuid
   user-id :- s/Uuid
   player-ids :- [s/Uuid]
   status :- s/Str
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        _ (when (nil? pelada)
            (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"})))
        org-id (:organization-id pelada)]
    (auth/require-organization-admin! user-id org-id db)
    (db.attendance/batch-upsert-attendance pelada-id player-ids status db)))

(s/defn close-attendance :- s/Any
  [pelada-id :- s/Uuid
   user-id :- s/Uuid
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        _ (when (nil? pelada)
            (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"})))
        org-id (:organization-id pelada)]
    (auth/require-organization-admin! user-id org-id db)
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
   user-id :- s/Uuid
   player-id :- s/Uuid
   enabled? :- s/Bool
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        _ (when (nil? pelada)
            (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"})))
        org-id (:organization-id pelada)]
    (auth/require-organization-admin! user-id org-id db)
    (jdbc/with-transaction [tx db]
      (let [res (db.attendance/update-voting-enabled pelada-id player-id enabled? tx)]
        (when-not enabled?
          (db.vote/delete-votes-for-target pelada-id player-id tx))
        {:updated res}))))

