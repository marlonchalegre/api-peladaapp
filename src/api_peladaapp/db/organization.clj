(ns api-peladaapp.db.organization
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-organization :- s/Int
  [{:keys [name]} db]
  (sql/insert! db :organizations {:name name})
  (-> (jdbc/execute-one! db ["select last_insert_rowid() as id"]) :id int))

(s/defn get-organization [id db]
  (-> (sql/get-by-id db :organizations id) adapter.organization/db->model))

(s/defn update-organization :- s/Int
  [id {:keys [name]} db]
  (-> (sql/update! db :organizations {:name name} {:id id}) affected-rows-count))

(s/defn delete-organization :- s/Int
  [id db]
  (-> (sql/delete! db :organizations {:id id}) affected-rows-count))

(s/defn list-organizations [db limit offset]
  (->> (sql/query db ["select * from organizations order by id limit ? offset ?" limit offset])
       (map adapter.organization/db->model)))

(s/defn count-organizations :- s/Int
  [db]
  (-> (sql/query db ["select count(*) as count from organizations"]) first :count))

(s/defn get-statistics
  [id :- s/Int
   year :- s/Int
   db]
  (sql/query db ["SELECT me.player_id, u.name as player_name, me.event_type, count(*) as count
                  FROM \"MatchEvents\" me
                  JOIN \"Matches\" m ON me.match_id = m.id
                  JOIN \"Peladas\" p ON m.pelada_id = p.id
                  JOIN \"OrganizationPlayers\" op ON me.player_id = op.id
                  JOIN \"Users\" u ON op.user_id = u.id
                  WHERE p.organization_id = ?
                    AND strftime('%Y', p.scheduled_at) = ?
                  GROUP BY me.player_id, u.name, me.event_type"
                 id (str year)]))
