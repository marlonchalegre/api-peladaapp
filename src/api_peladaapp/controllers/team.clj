(ns api-peladaapp.controllers.team
  (:require
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.models.team :as models.team]
   [schema.core :as s]))

(s/defn create-team :- models.team/Team
  [team :- models.team/Team
   db]
  (let [id (db.team/insert-team team db)]
    (db.team/get-team id db)))

(s/defn get-team :- models.team/Team
  [team-id :- s/Uuid db]
  (let [team (db.team/get-team team-id db)]
    (if (nil? team)
      (throw (ex-info nil {:type :not-found :message "Team not found"}))
      team)))

(s/defn update-team :- models.team/Team
  [team-id :- s/Uuid team :- models.team/Team db]
  (let [rows (db.team/update-team team-id team db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Team not found"}))
      (db.team/get-team team-id db))))

(s/defn delete-team :- s/Int
  [team-id :- s/Uuid db]
  (let [rows (db.team/delete-team team-id db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Team not found"}))
      rows)))

(s/defn list-teams :- [models.team/Team]
  [pelada-id :- s/Uuid db]
  (db.team/list-pelada-teams pelada-id db))

(s/defn add-player :- models.team/TeamPlayer
  ([team-id :- s/Uuid player-id :- s/Uuid db]
   (add-player team-id player-id false db))
  ([team-id :- s/Uuid player-id :- s/Uuid is-goalkeeper :- s/Bool db]
   (db.team/add-player-to-team team-id player-id is-goalkeeper db)
   {:team-id team-id :player-id player-id :is-goalkeeper is-goalkeeper}))

(s/defn remove-player :- s/Int
  [team-id :- s/Uuid player-id :- s/Uuid db]
  (db.team/remove-player-from-team team-id player-id db))