(ns api-peladaapp.db.player
  (:require
   [api-peladaapp.adapters.player :as adapter.player]
   [clojure.string :as str]
   [next.jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-player :- s/Int
  [{:keys [user-id organization-id grade position-id member-type]}
   db]
  (-> (sql/insert! db :organizationplayers {:user_id user-id
                                            :organization_id organization-id
                                            :grade grade
                                            :position_id position-id
                                            :member_type (or member-type "convidado")})
      affected-rows-count))

(s/defn update-player :- s/Int
  [id player db]
  (let [db-row (cond-> {}
                 (contains? player :grade) (assoc :grade (:grade player))
                 (contains? player :position-id) (assoc :position_id (:position-id player))
                 (contains? player :member-type) (assoc :member_type (:member-type player)))]
    (-> (sql/update! db :organizationplayers db-row {:id id})
        affected-rows-count)))

(s/defn update-player-grade :- s/Int
  "Surgically update a player's grade."
  [id grade db]
  (-> (sql/update! db :organizationplayers {:grade grade} {:id id})
      affected-rows-count))

(s/defn delete-player :- s/Int
  [id db]
  (-> (sql/delete! db :organizationplayers {:id id})
      affected-rows-count))

(s/defn get-player [id db]
  (-> (sql/get-by-id db :organizationplayers id)
      adapter.player/db->model))

(s/defn get-org-player-by-user-id :- s/Any
  [user-id organization-id db]
  (some-> (sql/find-by-keys db :organizationplayers {:user_id user-id :organization_id organization-id})
          first
          adapter.player/db->model))

(defn- position-string->id [pos]
  (case (some-> pos str/lower-case)
    "goalkeeper" 1
    "defender" 2
    "midfielder" 3
    "striker" 4
    nil))

(s/defn list-players-by-organization [organization-id db]
  (->> (next.jdbc/execute! db ["SELECT op.*, u.name as user_name, u.username as user_username, u.email as user_email, u.position as user_position 
                                FROM organizationplayers op 
                                JOIN users u ON op.user_id = u.id 
                                WHERE op.organization_id = ?" organization-id] opts)
       (map (fn [row]
              (if (nil? (:position_id row))
                (assoc row :position_id (position-string->id (:user_position row)))
                row)))
       (map adapter.player/db->model)
       vec))
