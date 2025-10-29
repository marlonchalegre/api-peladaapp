(ns api-100folego.db.player
  (:require [api-100folego.adapters.player :as adapter.player]
            [next.jdbc.sql :as sql]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-player :- s/Int
  [{:keys [user_id organization_id grade position_id]}
   db]
  (-> (sql/insert! (db) :organizationplayers {:user_id user_id
                                              :organization_id organization_id
                                              :grade grade
                                              :position_id position_id})
      affected-rows-count))

(s/defn update-player :- s/Int
  [id player db]
  (-> (sql/update! (db) :organizationplayers (select-keys player [:grade :position_id]) {:id id})
      affected-rows-count))

(s/defn delete-player :- s/Int
  [id db]
  (-> (sql/delete! (db) :organizationplayers {:id id})
      affected-rows-count))

(s/defn get-player [id db]
  (-> (sql/get-by-id (db) :organizationplayers id)
      adapter.player/db->model))

(s/defn list-players-by-organization [organization-id db]
  (->> (sql/find-by-keys (db) :organizationplayers {:organization_id organization-id})
       (map adapter.player/db->model)))
