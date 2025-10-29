(ns api-peladaapp.db.pelada
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
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
    (with-open [conn (jdbc/get-connection (db))]
      (sql/insert! conn :peladas row)
      (-> (jdbc/execute-one! conn ["select last_insert_rowid() as id"]) :id int))))

(s/defn get-pelada :- s/Any
  [id :- s/Int
   db]
  (-> (sql/get-by-id (db) :peladas id)
      adapter.pelada/db->model))

(s/defn update-pelada :- s/Int
  [id :- s/Int
   pelada
   db]
  (-> (sql/update! (db) :peladas (select-keys pelada [:organization_id :scheduled_at :num_teams :players_per_team :status]) {:id id})
      affected-rows-count))

(s/defn delete-pelada :- s/Int
  [id :- s/Int
   db]
  (-> (sql/delete! (db) :peladas {:id id})
      affected-rows-count))

(s/defn list-peladas :- [s/Any]
  [organization-id :- s/Int
   db]
  (->> (sql/find-by-keys (db) :peladas {:organization_id organization-id})
       (map adapter.pelada/db->model)))
