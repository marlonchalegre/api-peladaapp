(ns api-peladaapp.controllers.team
  (:require
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.team :as models.team]
   [next.jdbc :as jdbc]
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
      (throw (ex-info "Team not found" {:type :not-found :message "Team not found"}))
      team)))

(s/defn update-team :- models.team/Team
  [team-id :- s/Uuid team :- models.team/Team db]
  (let [rows (db.team/update-team team-id team db)]
    (if (zero? rows)
      (throw (ex-info "Team not found" {:type :not-found :message "Team not found"}))
      (db.team/get-team team-id db))))

(s/defn delete-team :- s/Int
  [team-id :- s/Uuid db]
  (let [rows (db.team/delete-team team-id db)]
    (if (zero? rows)
      (throw (ex-info "Team not found" {:type :not-found :message "Team not found"}))
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

(s/defn replace-all-teams-players!
  [pelada-id :- s/Uuid teams-map db]
  (jdbc/with-transaction [tx db]
    (let [valid-team-ids (set (map :id (db.team/list-pelada-teams pelada-id tx)))
          assignments (vec (for [[team-id-raw player-ids] teams-map
                                 :let [tid (misc/as-uuid (name team-id-raw))]
                                 :when (contains? valid-team-ids tid)
                                 pid-raw player-ids
                                 :let [pid (misc/as-uuid pid-raw)]
                                 :when pid]
                             {:team_id tid
                              :player_id pid
                              :is_goalkeeper false}))]
      (db.team/clear-teams-players pelada-id tx)
      (when (seq assignments)
        (db.team/add-team-players-batch! assignments tx))
      {:assigned (count assignments)})))