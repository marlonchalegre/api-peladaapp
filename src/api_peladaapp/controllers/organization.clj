(ns api-peladaapp.controllers.organization
  (:require [api-peladaapp.db.organization :as db.organization]
            [api-peladaapp.db.admin :as db.admin]
            [schema.core :as s]))

(s/defn create-organization :- s/Any [org user-id db]
  (let [id (db.organization/insert-organization org db)]
    ;; Add creator as admin (if user-id is provided)
    (when user-id
      (db.admin/insert-organization-admin {:organization_id id :user_id user-id} db))
    (db.organization/get-organization id db)))

(s/defn get-organization [id db]
  (db.organization/get-organization id db))

(s/defn update-organization :- s/Int [id org db]
  (db.organization/update-organization id org db))

(s/defn delete-organization :- s/Int [id db]
  (db.organization/delete-organization id db))

(s/defn list-organizations [db]
  (db.organization/list-organizations db))
