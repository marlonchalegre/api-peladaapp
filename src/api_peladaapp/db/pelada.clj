(ns api-peladaapp.db.pelada
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.user :as db.user]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count
  [result]
  (-> result vals first))

(s/defn insert-pelada :- s/Int
  [{:keys [organization_id scheduled_at num_teams players_per_team]}
   db]
  (let [row (cond-> {:organization_id organization_id}
              scheduled_at (assoc :scheduled_at scheduled_at)
              num_teams (assoc :num_teams num_teams)
              players_per_team (assoc :players_per_team players_per_team))]
    (sql/insert! db :Peladas row)
    (-> (jdbc/execute-one! db ["select last_insert_rowid() as id"]) :id int)))

(s/defn get-pelada :- s/Any
  [id :- s/Int
   db]
  (-> (sql/get-by-id db :Peladas id)
      adapter.pelada/db->model))

(s/defn update-pelada :- s/Int
  [id :- s/Int
   pelada
   db]
  (-> (sql/update! db :Peladas (select-keys pelada [:organization_id :scheduled_at :num_teams :players_per_team :status :closed_at]) {:id id})
      affected-rows-count))

(s/defn delete-pelada :- s/Int
  [id :- s/Int
   db]
  (-> (sql/delete! db :Peladas {:id id})
      affected-rows-count))

(s/defn list-peladas :- [s/Any]
  [organization-id :- s/Int
   db]
  (->> (sql/find-by-keys db :Peladas {:organization_id organization-id})
       (map adapter.pelada/db->model)))

(s/defn get-pelada-full-details :- s/Any
  [pelada-id :- s/Int
   db]
  (let [pelada (get-pelada pelada-id db)
        organization-id (:organization_id pelada)
        all-org-players (db.player/list-players-by-organization organization-id db)
        all-users (db.user/list-users db 0 100000) ;; Fetch a large number of users for now, can optimize later if needed
        users-map (into {} (map (juxt :id identity)) all-users)
        teams (db.team/list-pelada-teams pelada-id db)
        team-players-raw (db.team/list-team-players-by-pelada pelada-id db)

        ; Group team players by team ID
        team-players-grouped (group-by :team_id team-players-raw)

        ; Add players to teams
        teams-with-players (map (fn [team]
                                  (assoc team :players (map (fn [team-player]
                                                              (let [player (first (filter #(= (:player_id team-player) (:id %)) all-org-players))
                                                                    user (get users-map (:user_id player))]
                                                                (assoc player :user user)))
                                                            (get team-players-grouped (:id team) []))))
                                teams)

        ; Identify players already assigned to teams
        assigned-player-ids (set (map :player_id team-players-raw))

        ; Filter available players (not in any team for this pelada)
        available-players (filter #(not (assigned-player-ids (:id %))) all-org-players)

        ; Add user info to available players
        available-players-with-users (map (fn [player]
                                            (assoc player :user (get users-map (:user_id player))))
                                          available-players)]

    {:pelada pelada
     :teams teams-with-players
     :available_players available-players-with-users
     :users_map users-map ;; This might be useful for the frontend to build its own userIdToName map
     :org_players_map (into {} (map (juxt :id identity)) all-org-players)}))
