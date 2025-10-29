(ns api-peladaapp.db.organization
  (:require [api-peladaapp.adapters.organization :as adapter.organization]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-organization :- s/Int
  [{:keys [name]} db]
  (with-open [conn (jdbc/get-connection (db))]
    (sql/insert! conn :organizations {:name name})
    (-> (jdbc/execute-one! conn ["select last_insert_rowid() as id"]) :id int)))

(s/defn get-organization [id db]
  (-> (sql/get-by-id (db) :organizations id) adapter.organization/db->model))

(s/defn update-organization :- s/Int
  [id {:keys [name]} db]
  (-> (sql/update! (db) :organizations {:name name} {:id id}) affected-rows-count))

(s/defn delete-organization :- s/Int
  [id db]
  (-> (sql/delete! (db) :organizations {:id id}) affected-rows-count))

(s/defn list-organizations [db]
  (->> (sql/query (db) ["select * from organizations"]) (map adapter.organization/db->model)))
