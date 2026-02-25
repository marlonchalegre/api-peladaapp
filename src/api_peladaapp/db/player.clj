(ns api-peladaapp.db.player
  (:require
   [api-peladaapp.adapters.player :as adapter.player]
   [next.jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-player :- s/Int
  [{:keys [user-id organization-id grade position-id]}
   db]
  (-> (sql/insert! db :organizationplayers {:user_id user-id
                                            :organization_id organization-id
                                            :grade grade
                                            :position_id position-id})
      affected-rows-count))

(s/defn update-player :- s/Int
  [id player db]
  (let [db-row (cond-> {}
                 (contains? player :grade) (assoc :grade (:grade player))
                 (contains? player :position-id) (assoc :position_id (:position-id player)))]
    (-> (sql/update! db :organizationplayers db-row {:id id})
        affected-rows-count)))

(s/defn delete-player :- s/Int
  [id db]
  (-> (sql/delete! db :organizationplayers {:id id})
      affected-rows-count))

(s/defn get-player [id db]
  (-> (sql/get-by-id db :organizationplayers id)
      adapter.player/db->model))

(s/defn get-org-player-by-user-id :- s/Any
  [user-id organization-id db]
  (let [unqualify #(update-keys % (comp keyword name))]
    (some-> (sql/find-by-keys db :organizationplayers {:user_id user-id :organization_id organization-id})
            first
            unqualify)))

(s/defn list-players-by-organization [organization-id db]
  (->> (next.jdbc/execute! db ["SELECT op.*, u.name as user_name, u.username as user_username, u.email as user_email 
                                FROM organizationplayers op 
                                JOIN users u ON op.user_id = u.id 
                                WHERE op.organization_id = ?" organization-id])
       (map adapter.player/db->model)
       vec))
