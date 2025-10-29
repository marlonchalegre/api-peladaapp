(ns api-100folego.controllers.player
  (:require [api-100folego.db.player :as db.player]
            [schema.core :as s]))

(s/defn create-player :- s/Int
  [player db]
  (db.player/insert-player player db))

(s/defn get-player :- s/Any
  [player-id :- s/Int db]
  (db.player/get-player player-id db))

(s/defn update-player :- s/Int
  [player-id :- s/Int player db]
  (db.player/update-player player-id player db))

(s/defn delete-player :- s/Int
  [player-id :- s/Int db]
  (db.player/delete-player player-id db))

(s/defn list-players :- [s/Any]
  [organization-id :- s/Int db]
  (db.player/list-players-by-organization organization-id db))
