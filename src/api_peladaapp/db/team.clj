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
  (-> (sql/insert! (db) :teams {:pelada_id pelada_id :name name})
      affected-rows-count))

(s/defn get-team [id db]
  (-> (sql/get-by-id (db) :teams id)
      adapter.team/db->model))

(s/defn update-team :- s/Int
  [id team db]
  (-> (sql/update! (db) :teams (select-keys team [:name]) {:id id})
      affected-rows-count))

(s/defn delete-team :- s/Int
  [id db]
  (-> (sql/delete! (db) :teams {:id id}) affected-rows-count))

(s/defn list-pelada-teams [pelada-id db]
  (->> (sql/find-by-keys (db) :teams {:pelada_id pelada-id})
       (map adapter.team/db->model)))

(s/defn validate-player-belongs-to-pelada-org :- (s/maybe s/Bool)
  "Validates if a player belongs to the same organization as the pelada of the team"
  [team-id player-id db]
  (let [query ["SELECT 1 FROM OrganizationPlayers op
                INNER JOIN Teams t ON t.id = ?
                INNER JOIN Peladas p ON p.id = t.pelada_id
                WHERE op.id = ? AND op.organization_id = p.organization_id"
               team-id player-id]
        result (jdbc/execute-one! (db) query)]
    (some? result)))

(s/defn add-player-to-team :- s/Int
  [team-id player-id db]
  (when-not (validate-player-belongs-to-pelada-org team-id player-id db)
    (throw (ex-info "Player does not belong to the pelada's organization"
                    {:type :validation-error
                     :message "Player does not belong to the pelada's organization"
                     :team-id team-id
                     :player-id player-id})))
  (-> (sql/insert! (db) :teamplayers {:team_id team-id :player_id player-id})
      affected-rows-count))

(s/defn remove-player-from-team :- s/Int
  [team-id player-id db]
  (-> (sql/delete! (db) :teamplayers {:team_id team-id :player_id player-id})
      affected-rows-count))

(s/defn list-team-players [team-id db]
  (->> (sql/find-by-keys (db) :teamplayers {:team_id team-id})
       (map (fn [m] (update-keys m (comp keyword name))))))
