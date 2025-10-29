(ns api-peladaapp.db.admin
  (:require [api-peladaapp.adapters.admin :as adapter.admin]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-organization-admin :- s/Int
  [{:keys [organization_id user_id]} db]
  (with-open [conn (jdbc/get-connection (db))]
    (sql/insert! conn :OrganizationAdmins {:organization_id organization_id
                                            :user_id user_id})
    (-> (jdbc/execute-one! conn ["select last_insert_rowid() as id"]) :id int)))

(s/defn get-organization-admin [id db]
  (-> (sql/get-by-id (db) :OrganizationAdmins id) adapter.admin/db->model))

(s/defn delete-organization-admin :- s/Int
  [id db]
  (-> (sql/delete! (db) :OrganizationAdmins {:id id}) affected-rows-count))

(s/defn delete-organization-admin-by-org-and-user :- s/Int
  [organization_id user_id db]
  (-> (sql/delete! (db) :OrganizationAdmins {:organization_id organization_id
                                               :user_id user_id})
      affected-rows-count))

(s/defn list-admins-by-organization [organization_id db]
  (->> (jdbc/execute! (db) ["select oa.*, u.name as user_name, u.email as user_email 
                             from OrganizationAdmins oa 
                             join Users u on oa.user_id = u.id 
                             where oa.organization_id = ?" organization_id])
       (map adapter.admin/db->model)))

(s/defn list-organizations-by-admin [user_id db]
  (->> (jdbc/execute! (db) ["select oa.*, o.name as organization_name 
                             from OrganizationAdmins oa 
                             join Organizations o on oa.organization_id = o.id 
                             where oa.user_id = ?" user_id])
       (map adapter.admin/db->model)))

(s/defn is-user-admin-of-organization? :- s/Bool
  [user_id organization_id db]
  (let [result (jdbc/execute-one! (db) 
                                   ["select count(*) as count from OrganizationAdmins where user_id = ? and organization_id = ?" 
                                    user_id organization_id])]
    (> (:count result) 0)))
