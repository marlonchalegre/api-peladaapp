(ns api-peladaapp.controllers.player
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.models.player :as models.player]
   [schema.core :as s]))

(defn- validate-member-type!
  [player]
  (when (contains? #{"mensalista_temporario" "diarista_temporario"} (:member-type player))
    (throw (ex-info "Temporary member types cannot be assigned directly"
                    {:type :bad-request :message "Temporary member types must be managed by the Substitution feature"}))))

(s/defn create-player :- models.player/Player
  [player :- models.player/Player
   db]
  (validate-member-type! player)
  (let [id (db.player/insert-player player db)]
    (db.player/get-player id db)))

(s/defn get-player :- models.player/Player
  [player-id :- s/Uuid db]
  (let [player (db.player/get-player player-id db)]
    (if (nil? player)
      (throw (ex-info nil {:type :not-found :message "Player not found"}))
      player)))

(s/defn update-player :- models.player/Player
  [player-id :- s/Uuid player :- models.player/Player db]
  (validate-member-type! player)
  (let [rows (db.player/update-player player-id player db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Player not found"}))
      (db.player/get-player player-id db))))

(s/defn delete-player :- s/Int
  [player-id :- s/Uuid db]
  (let [rows (db.player/delete-player player-id db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Player not found"}))
      rows)))

(s/defn list-players :- [models.player/Player]
  [organization-id :- s/Uuid db]
  (db.player/list-players-by-organization organization-id db))