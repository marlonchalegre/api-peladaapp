(ns api-peladaapp.controllers.admin
  (:require [api-peladaapp.db.admin :as db.admin]
            [api-peladaapp.db.organization :as db.organization]
            [schema.core :as s]))

(s/defn add-organization-admin :- s/Any [admin db]
  (let [id (db.admin/insert-organization-admin admin db)]
    (db.admin/get-organization-admin id db)))

(s/defn get-organization-admin [id db]
  (db.admin/get-organization-admin id db))

(s/defn remove-organization-admin :- s/Int [id db]
  (db.admin/delete-organization-admin id db))

(s/defn remove-organization-admin-by-org-and-user :- s/Int [organization_id user_id db]
  (db.admin/delete-organization-admin-by-org-and-user organization_id user_id db))

(s/defn list-organization-admins [organization_id db]
  (db.admin/list-admins-by-organization organization_id db))

(s/defn list-user-admin-organizations [user_id db]
  (let [admin-records (db.admin/list-organizations-by-admin user_id db)]
    (map (fn [admin]
           (let [org (db.organization/get-organization (:organization_id admin) db)]
             (assoc admin 
                    :organization_id (:organization_id admin)
                    :organization_name (:name org))))
         admin-records)))

(s/defn is-user-admin-of-organization? :- s/Bool [user_id organization_id db]
  (db.admin/is-user-admin-of-organization? user_id organization_id db))
