(ns api-peladaapp.db.admin
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-organization-admin :- s/Int
  [{:keys [organization-id user-id]} db]
  (-> (sql/insert! db :organizationadmins {:organization_id organization-id
                                           :user_id user-id})
      affected-rows-count))

(s/defn get-organization-admin [id db]
  (-> (sql/get-by-id db :organizationadmins id) adapter.admin/db->model))

(s/defn delete-organization-admin :- s/Int
  [id db]
  (-> (sql/delete! db :organizationadmins {:id id}) affected-rows-count))

(s/defn delete-organization-admin-by-org-and-user :- s/Int
  [organization_id user_id db]
  (-> (sql/delete! db :organizationadmins {:organization_id organization_id
                                           :user_id user_id})
      affected-rows-count))

(s/defn list-admins-by-organization [organization_id db]
  (->> (jdbc/execute! db ["select oa.*, u.name as user_name, u.email as user_email
                             from organizationadmins oa
                             join users u on oa.user_id = u.id
                             where oa.organization_id = ?" organization_id])
       (map adapter.admin/db->model)))

(s/defn list-organizations-by-admin [user_id db]
  (->> (jdbc/execute! db ["select oa.*, o.name as organization_name
                             from organizationadmins oa
                             join organizations o on oa.organization_id = o.id
                             where oa.user_id = ?" user_id])
       (map adapter.admin/db->model)))

(s/defn is-user-admin-of-organization? :- s/Bool
  [user_id organization_id db]
  (let [result (jdbc/execute-one! db
                                  ["select count(*) as count from organizationadmins where user_id = ? and organization_id = ?"
                                   user_id organization_id])]
    (> (:count result) 0)))
