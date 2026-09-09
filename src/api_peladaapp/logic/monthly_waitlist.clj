(ns api-peladaapp.logic.monthly-waitlist
  (:require
   [api-peladaapp.adapters.monthly-waitlist :as adapter.monthly-waitlist]
   [api-peladaapp.db.monthly-waitlist :as db.monthly-waitlist]
   [api-peladaapp.db.player :as db.player]
   [schema.core :as s]))

(s/defn ^:private validate-player-in-org!
  [org-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [player (db.player/get-player player-id db)]
    (when-not player
      (throw (ex-info "Player not found" {:type :not-found :message "Player not found"})))
    (when-not (= (:organization-id player) org-id)
      (throw (ex-info "Player does not belong to this organization"
                      {:type :bad-request :message "Player does not belong to this organization"})))
    player))

(s/defn add-candidate!
  [org-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [player (validate-player-in-org! org-id player-id db)]
    (when (contains? #{"mensalista" "mensalista_temporario"} (:member-type player))
      (throw (ex-info "Player is already a monthly player"
                      {:type :bad-request :message "Player is already a monthly player"})))
    (when (db.monthly-waitlist/get-waitlist-entry org-id player-id db)
      (throw (ex-info "Player is already in the waitlist"
                      {:type :bad-request :message "Player is already in the waitlist"})))
    (let [waitlist-id (db.monthly-waitlist/add-to-waitlist! org-id player-id db)]
      {:id waitlist-id :status :success})))

(s/defn remove-candidate!
  [org-id :- s/Uuid
   player-id :- s/Uuid
   user-id :- s/Uuid
   is-admin? :- s/Bool
   db]
  (let [player (validate-player-in-org! org-id player-id db)]
    (when-not is-admin?
      (when-not (= (:user-id player) user-id)
        (throw (ex-info "Forbidden" {:type :forbidden :message "You cannot remove another player from the waitlist"}))))
    (db.monthly-waitlist/remove-from-waitlist! org-id player-id db)
    {:status :success}))

(s/defn promote-candidate!
  [org-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (validate-player-in-org! org-id player-id db)
  (when-not (db.monthly-waitlist/get-waitlist-entry org-id player-id db)
    (throw (ex-info "Player is not in the waitlist"
                    {:type :bad-request :message "Player is not in the waitlist"})))
  (db.monthly-waitlist/promote-player! org-id player-id db)
  {:status :success})

(s/defn get-waitlist-status
  [org-id :- s/Uuid
   user-id :- s/Uuid
   db]
  (let [player (db.player/get-org-player-by-user-id user-id org-id db)]
    (if-not player
      {:in-queue false}
      (if-let [entry (db.monthly-waitlist/get-waitlist-entry org-id (:id player) db)]
        {:in-queue true
         :entry (adapter.monthly-waitlist/db->response entry)}
        {:in-queue false}))))
