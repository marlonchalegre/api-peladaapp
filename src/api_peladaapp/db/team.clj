(ns api-peladaapp.db.team
  (:require [api-peladaapp.adapters.team :as adapter.team]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-team :- s/Int
  [{:keys [pelada_id name]}
   db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/insert! conn :teams {:pelada_id pelada_id :name name})
        affected-rows-count)))

(s/defn get-team [id db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/get-by-id conn :teams id)
        adapter.team/db->model)))

(s/defn update-team :- s/Int
  [id team db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/update! conn :teams (select-keys team [:name]) {:id id})
        affected-rows-count)))

(s/defn delete-team :- s/Int
  [id db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/delete! conn :teams {:id id}) affected-rows-count)))

(s/defn list-pelada-teams [pelada-id db]
  (with-open [conn (jdbc/get-connection (db))]
    (->> (sql/find-by-keys conn :teams {:pelada_id pelada-id})
         (map adapter.team/db->model))))

(s/defn validate-player-belongs-to-pelada-org :- (s/maybe s/Bool)
  "Validates if a player belongs to the same organization as the pelada of the team"
  [team-id player-id db]
  (with-open [conn (jdbc/get-connection (db))]
    (let [query ["SELECT 1 FROM OrganizationPlayers op
                  INNER JOIN Teams t ON t.id = ?
                  INNER JOIN Peladas p ON p.id = t.pelada_id
                  WHERE op.id = ? AND op.organization_id = p.organization_id"
                 team-id player-id]
          result (jdbc/execute-one! conn query)]
      (some? result))))

(s/defn validate-player-not-in-another-team-of-same-pelada :- (s/maybe s/Bool)
  "Validates if a player is not already in another team of the same pelada"
  [team-id player-id db]
  (with-open [conn (jdbc/get-connection (db))]
    (let [query ["SELECT 1 FROM TeamPlayers tp
                  INNER JOIN Teams t ON t.id = tp.team_id
                  WHERE t.pelada_id = (SELECT pelada_id FROM Teams WHERE id = ?) AND tp.player_id = ?"
                 team-id player-id]
          result (jdbc/execute-one! conn query)]
      (nil? result))))

(s/defn add-player-to-team :- s/Int
  [team-id player-id db]
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
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/insert! conn :teamplayers {:team_id team-id :player_id player-id})
        affected-rows-count)))

(s/defn remove-player-from-team :- s/Int
  [team-id player-id db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/delete! conn :teamplayers {:team_id team-id :player_id player-id})
        affected-rows-count)))

(s/defn list-team-players [team-id db]
  (with-open [conn (jdbc/get-connection (db))]
    (->> (sql/find-by-keys conn :teamplayers {:team_id team-id})
         (map (fn [m] (update-keys m (comp keyword name)))))))
