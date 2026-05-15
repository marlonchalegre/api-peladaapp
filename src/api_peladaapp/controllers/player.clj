(ns api-peladaapp.controllers.player
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.models.player :as models.player]
   [schema.core :as s]))

(s/defn create-player :- models.player/Player
  [player :- models.player/Player
   db]
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