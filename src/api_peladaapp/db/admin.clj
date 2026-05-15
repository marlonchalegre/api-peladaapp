(ns api-peladaapp.db.admin
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn insert-organization-admin :- s/Uuid
  [{:keys [organization-id user-id]} :- {:organization-id s/Uuid :user-id s/Uuid} db]
  (let [query (-> (h/insert-into :OrganizationAdmins)
                  (h/values [{:organization_id organization-id :user_id user-id}])
                  (h/returning :id))
        result (jdbc/execute-one! db (hsql/format query) hsql/opts)]
    (:id result)))

(s/defn get-organization-admin [id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :OrganizationAdmins)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        adapter.admin/db->model)))

(s/defn delete-organization-admin :- s/Int
  [id :- s/Uuid db]
  (let [query (-> (h/delete-from :OrganizationAdmins)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query))
        hsql/affected-rows-count)))

(s/defn delete-organization-admin-by-org-and-user :- s/Int
  [organization-id :- s/Uuid user-id :- s/Uuid db]
  (let [query (-> (h/delete-from :OrganizationAdmins)
                  (h/where [:= :organization_id organization-id] [:= :user_id user-id]))]
    (-> (jdbc/execute-one! db (hsql/format query))
        hsql/affected-rows-count)))

(s/defn list-admins-by-organization [organization-id :- s/Uuid db]
  (let [query (-> (h/select :oa.* [:u.name :user_name] [:u.username :user_username] [:u.position :user_position] [:u.avatar_filename :avatar_filename])
                  (h/from [:OrganizationAdmins :oa])
                  (h/join [:Users :u] [:= :oa.user_id :u.id])
                  (h/where [:= :oa.organization_id organization-id]))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map adapter.admin/db->model))))

(s/defn list-organizations-by-admin [user-id :- s/Uuid db]
  (let [query (-> (h/select :oa.* [:o.name :organization_name])
                  (h/from [:OrganizationAdmins :oa])
                  (h/join [:Organizations :o] [:= :oa.organization_id :o.id])
                  (h/where [:= :oa.user_id user-id]))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map adapter.admin/db->model))))

(s/defn count-admins-by-organization :- s/Int
  [organization-id :- s/Uuid db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :OrganizationAdmins)
                  (h/where [:= :organization_id organization-id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        :count
        int)))

(s/defn is-user-admin-of-organization? :- s/Bool
  [user-id :- s/Uuid organization-id :- s/Uuid db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :OrganizationAdmins)
                  (h/where [:= :user_id [:cast user-id :uuid]] [:= :organization_id [:cast organization-id :uuid]]))
        res (jdbc/execute-one! db (hsql/format query) hsql/opts)]
    (> (int (or (:count res) 0)) 0)))
