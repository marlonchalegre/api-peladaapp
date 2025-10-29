(ns api-100folego.controllers.team
  (:require [api-100folego.db.team :as db.team]
            [schema.core :as s]))

(s/defn create-team :- s/Int
  [team db]
  (db.team/insert-team team db))

(s/defn get-team :- s/Any
  [team-id :- s/Int db]
  (db.team/get-team team-id db))

(s/defn update-team :- s/Int
  [team-id :- s/Int team db]
  (db.team/update-team team-id team db))

(s/defn delete-team :- s/Int
  [team-id :- s/Int db]
  (db.team/delete-team team-id db))

(s/defn list-teams :- [s/Any]
  [pelada-id :- s/Int db]
  (db.team/list-pelada-teams pelada-id db))

(s/defn add-player :- s/Int
  [team-id :- s/Int player-id :- s/Int db]
  (db.team/add-player-to-team team-id player-id db))

(s/defn remove-player :- s/Int
  [team-id :- s/Int player-id :- s/Int db]
  (db.team/remove-player-from-team team-id player-id db))
