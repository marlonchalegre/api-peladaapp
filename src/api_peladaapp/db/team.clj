(ns api-peladaapp.db.team
  (:require
   [api-peladaapp.adapters.team :as adapter.team]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- unqualify-row [row]
  (into {}
        (map (fn [[k v]]
               (let [kw (if (keyword? k) (keyword (name k)) k)]
                 [kw v])))
        row))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-team :- s/Int
  [{:keys [pelada-id name]}
   db]
  (jdbc/execute! db ["PRAGMA busy_timeout = 5000"])
  (-> (sql/insert! db :teams {:pelada_id pelada-id :name name})
      affected-rows-count))

(s/defn get-team [id db]
  (-> (sql/get-by-id db :teams id)
      adapter.team/db->model))

(s/defn update-team :- s/Int
  [id team db]
  (-> (sql/update! db :teams (select-keys team [:name]) {:id id})
      affected-rows-count))

(s/defn delete-team :- s/Int
  [id db]
  (-> (sql/delete! db :teams {:id id}) affected-rows-count))

(s/defn list-pelada-teams [pelada-id db]
  (->> (sql/find-by-keys db :teams {:pelada_id pelada-id})
       (map adapter.team/db->model)))

(s/defn validate-player-belongs-to-pelada-org :- (s/maybe s/Bool)
  "Validates if a player belongs to the same organization as the pelada of the team"
  [team-id player-id db]
  (let [query ["SELECT 1 FROM OrganizationPlayers op
                  INNER JOIN Teams t ON t.id = ?
                  INNER JOIN Peladas p ON p.id = t.pelada_id
                  WHERE op.id = ? AND op.organization_id = p.organization_id"
               team-id player-id]
        result (jdbc/execute-one! db query)]
    (some? result)))

(s/defn validate-player-not-in-another-team-of-same-pelada :- (s/maybe s/Bool)
  "Validates if a player is not already in another team of the same pelada"
  [team-id player-id db]
  (let [query ["SELECT 1 FROM TeamPlayers tp
                  INNER JOIN Teams t ON t.id = tp.team_id
                  WHERE t.pelada_id = (SELECT pelada_id FROM Teams WHERE id = ?) AND tp.player_id = ?"
               team-id player-id]
        result (jdbc/execute-one! db query)]
    (nil? result)))

(s/defn validate-team-not-full :- (s/maybe s/Bool)
  "Validates if the team has not reached the player limit.
   Note: Fixed goalkeepers are global and not in TeamPlayers."
  [team-id db]
  (let [query ["SELECT p.players_per_team as max_players, count(tp.player_id) as current_count
                FROM Teams t
                JOIN Peladas p ON p.id = t.pelada_id
                LEFT JOIN TeamPlayers tp ON tp.team_id = t.id
                WHERE t.id = ?
                GROUP BY p.players_per_team"
               team-id]
        result (jdbc/execute-one! db query {:builder-fn next.jdbc.result-set/as-unqualified-lower-maps})]
    (if (and (:max_players result)
             (>= (:current_count result) (:max_players result)))
      false
      true)))

(s/defn add-player-to-team :- s/Int
  ([team-id player-id db]
   (add-player-to-team team-id player-id false db))
  ([team-id player-id is-goalkeeper db]
   (when-not (validate-player-belongs-to-pelada-org team-id player-id db)
     (throw (ex-info "Player does not belong to the pelada's organization"
                     {:type :validation-error
                      :message "Player does not belong to the pelada's organization"
                      :team-id team-id
                      :player-id player-id})))
   (when-not (validate-player-not-in-another-team-of-same-pelada team-id player-id db)
     (throw (ex-info "Player is already in a team for this pelada"
                     {:type :validation-error
                      :message "Player is already in a team for this pelada"
                      :team-id team-id
                      :player-id player-id})))
   ;; Note: is-goalkeeper is ignored for TeamPlayers validation now because they are global
   (when-not (validate-team-not-full team-id db)
     (throw (ex-info "Team is full"
                     {:type :validation-error
                      :message "Team is full"
                      :team-id team-id})))
   (-> (sql/insert! db :teamplayers {:team_id team-id :player_id player-id :is_goalkeeper (if is-goalkeeper 1 0)})
       affected-rows-count)))

(s/defn remove-player-from-team :- s/Int
  [team-id player-id db]
  (-> (sql/delete! db :teamplayers {:team_id team-id :player_id player-id})
      affected-rows-count))

(s/defn clear-teams-players :- s/Int
  "Removes all players from all teams of a specific pelada"
  [pelada-id db]
  (let [query "DELETE FROM TeamPlayers WHERE team_id IN (SELECT id FROM Teams WHERE pelada_id = ?)"
        result (jdbc/execute! db [query pelada-id])]
    (affected-rows-count (first result))))

(s/defn list-team-players [team-id db]
  (->> (sql/find-by-keys db :teamplayers {:team_id team-id})
       (map unqualify-row)
       (map (fn [m]
              {:team-id (:team_id m)
               :player-id (:player_id m)
               :is-goalkeeper (not= 0 (:is_goalkeeper m))}))))

(s/defn list-team-players-by-pelada [pelada-id db]
  (->> (sql/query db ["SELECT tp.*, t.name as team_name, t.pelada_id
                        FROM TeamPlayers tp
                        JOIN Teams t ON tp.team_id = t.id
                        WHERE t.pelada_id = ?" pelada-id])
       (map unqualify-row)
       (map (fn [m]
              (assoc m :is_goalkeeper (not= 0 (:is_goalkeeper m)))))
       vec))

(s/defn list-team-players-with-names-by-pelada [pelada-id db]
  (->> (sql/query db ["SELECT tp.*, t.name as team_name, u.name as player_name
                        FROM TeamPlayers tp
                        JOIN Teams t ON tp.team_id = t.id
                        JOIN OrganizationPlayers op ON tp.player_id = op.id
                        JOIN Users u ON op.user_id = u.id
                        WHERE t.pelada_id = ?" pelada-id])
       (map unqualify-row)
       (map (fn [m]
              (assoc m :is_goalkeeper (not= 0 (:is_goalkeeper m)))))
       vec))
