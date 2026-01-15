(ns api-peladaapp.db.pelada
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.user :as db.user]
   [medley.core :as medley.core]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count
  [result]
  (-> result vals first))

(s/defn insert-pelada :- s/Int
  [{:keys [organization-id scheduled-at num-teams players-per-team]}
   db]
  (let [row (cond-> {:organization_id organization-id}
              scheduled-at (assoc :scheduled_at scheduled-at)
              num-teams (assoc :num_teams num-teams)
              players-per-team (assoc :players_per_team players-per-team))]
    (-> (sql/insert! db :peladas row)
        affected-rows-count)))

(s/defn get-pelada :- s/Any
  [id :- s/Int
   db]
  (-> (sql/get-by-id db :peladas id)
      adapter.pelada/db->model))

(s/defn update-pelada :- s/Int
  [id :- s/Int
   pelada
   db]
  (let [db-row (medley.core/assoc-some {}
                                      :organization_id (:organization-id pelada)
                                      :scheduled_at (:scheduled-at pelada)
                                      :num_teams (:num-teams pelada)
                                      :players_per_team (:players-per-team pelada)
                                      :status (:status pelada)
                                      :closed_at (:closed-at pelada))]
    (-> (sql/update! db :peladas db-row {:id id})
        affected-rows-count)))

(s/defn delete-pelada :- s/Int
  [id :- s/Int
   db]
  (-> (sql/delete! db :peladas {:id id})
      affected-rows-count))

(s/defn list-peladas :- [s/Any]
  [organization-id :- s/Int
   limit :- s/Int
   offset :- s/Int
   db]
  (->> (sql/query db ["select * from peladas where organization_id = ? order by id desc limit ? offset ?" organization-id limit offset])
       (map adapter.pelada/db->model)))

(s/defn count-peladas :- s/Int
  [organization-id :- s/Int
   db]
  (-> (sql/query db ["select count(*) as count from peladas where organization_id = ?" organization-id])
      first
      :count))

(s/defn get-pelada-full-details :- s/Any

  [pelada-id :- s/Int

   db]

  (if-let [pelada (get-pelada pelada-id db)]

    (let [organization-id (:organization-id pelada)

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

                                                                      user (get users-map (:user-id player))]

                                                                  (assoc player :user user)))

                                                              (get team-players-grouped (:id team) []))))

                                  teams)



          ; Identify players already assigned to teams

          assigned-player-ids (set (map :player_id team-players-raw))



          ; Filter available players (not in any team for this pelada)

          available-players (filter #(not (assigned-player-ids (:id %))) all-org-players)



          ; Add user info to available players

          available-players-with-users (map (fn [player]

                                              (assoc player :user (get users-map (:user-id player))))

                                            available-players)]



      {:pelada pelada

       :teams teams-with-players

       :available_players available-players-with-users

       :users_map users-map ;; This might be useful for the frontend to build its own userIdToName map

       :org_players_map (into {} (map (juxt :id identity)) all-org-players)})

    (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"}))))
