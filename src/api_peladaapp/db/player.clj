(ns api-peladaapp.db.player
  (:require [api-peladaapp.adapters.player :as adapter.player]
            [next.jdbc.sql :as sql]
            [next.jdbc :as jdbc]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-player :- s/Int
  [{:keys [user_id organization_id grade position_id]}
   db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/insert! conn :organizationplayers {:user_id user_id
                                              :organization_id organization_id
                                              :grade grade
                                              :position_id position_id})
        affected-rows-count)))

(s/defn update-player :- s/Int
  [id player db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/update! conn :organizationplayers (select-keys player [:grade :position_id]) {:id id})
        affected-rows-count)))

(s/defn delete-player :- s/Int
  [id db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/delete! conn :organizationplayers {:id id})
        affected-rows-count)))

(s/defn get-player [id db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/get-by-id conn :organizationplayers id)
        adapter.player/db->model)))

(s/defn get-org-player-by-user-id :- s/Any
  [user-id organization-id db]
  (with-open [conn (jdbc/get-connection (db))]
    (let [unqualify #(update-keys % (comp keyword name))]
      (some-> (sql/find-by-keys conn :organizationplayers {:user_id user-id :organization_id organization-id})
              first
              unqualify))))

(s/defn list-players-by-organization [organization-id db]
  (with-open [conn (jdbc/get-connection (db))]
    (->> (sql/find-by-keys conn :organizationplayers {:organization_id organization-id})
         (map adapter.player/db->model))))
