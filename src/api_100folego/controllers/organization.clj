(ns api-100folego.controllers.organization
  (:require [api-100folego.db.organization :as db.organization]
            [schema.core :as s]))

(s/defn create-organization :- s/Any [org db]
  (let [id (db.organization/insert-organization org db)]
    (db.organization/get-organization id db)))

(s/defn get-organization [id db]
  (db.organization/get-organization id db))

(s/defn update-organization :- s/Int [id org db]
  (db.organization/update-organization id org db))

(s/defn delete-organization :- s/Int [id db]
  (db.organization/delete-organization id db))

(s/defn list-organizations [db]
  (db.organization/list-organizations db))
