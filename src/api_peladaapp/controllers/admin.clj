(ns api-peladaapp.controllers.admin
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.models.admin :as models.admin]
   [schema.core :as s]))

(s/defn add-organization-admin :- models.admin/OrganizationAdmin
  [admin :- models.admin/NewOrganizationAdmin db]
  (let [id (db.admin/insert-organization-admin admin db)]
    (db.admin/get-organization-admin id db)))

(s/defn get-organization-admin :- models.admin/OrganizationAdmin
  [id :- s/Uuid db]
  (db.admin/get-organization-admin id db))

(s/defn remove-organization-admin :- s/Int
  [id :- s/Uuid db]
  (let [admin (db.admin/get-organization-admin id db)]
    (if admin
      (let [admins (db.admin/list-admins-by-organization (:organization-id admin) db)]
        (if (<= (count admins) 1)
          (throw (ex-info "Cannot remove the last administrator from the organization."
                          {:type :bad-request}))
          (db.admin/delete-organization-admin id db)))
      0)))

(s/defn remove-organization-admin-by-org-and-user :- s/Int
  [organization_id :- s/Uuid user_id :- s/Uuid db]
  (let [admins (db.admin/list-admins-by-organization organization_id db)]
    (if (<= (count admins) 1)
      (throw (ex-info "Cannot remove the last administrator from the organization."
                      {:type :bad-request}))
      (db.admin/delete-organization-admin-by-org-and-user organization_id user_id db))))

(s/defn list-organization-admins :- [models.admin/OrganizationAdmin]
  [organization_id :- s/Uuid db]
  (db.admin/list-admins-by-organization organization_id db))

(s/defn list-user-admin-organizations :- [models.admin/OrganizationAdmin]
  [user_id :- s/Uuid db]
  (db.admin/list-organizations-by-admin user_id db))

(s/defn is-user-admin-of-organization? :- s/Bool
  [user_id :- s/Uuid organization_id :- s/Uuid db]
  (db.admin/is-user-admin-of-organization? user_id organization_id db))