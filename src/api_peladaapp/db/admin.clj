(ns api-peladaapp.db.admin
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (-> res vals first)))

(s/defn insert-organization-admin :- s/Int
  [{:keys [organization-id user-id]} db]
  (let [query (-> (h/insert-into :OrganizationAdmins)
                  (h/values [{:organization_id organization-id :user_id user-id}])
                  (h/returning :id))
        result (jdbc/execute-one! db (hsql/format query) opts)]
    (:id result)))

(s/defn get-organization-admin [id db]
  (let [query (-> (h/select :*)
                  (h/from :OrganizationAdmins)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        adapter.admin/db->model)))

(s/defn delete-organization-admin :- s/Int
  [id db]
  (let [query (-> (h/delete-from :OrganizationAdmins)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query))
        affected-rows-count)))

(s/defn delete-organization-admin-by-org-and-user :- s/Int
  [organization-id user-id db]
  (let [query (-> (h/delete-from :OrganizationAdmins)
                  (h/where [:= :organization_id organization-id] [:= :user_id user-id]))]
    (-> (jdbc/execute-one! db (hsql/format query))
        affected-rows-count)))

(s/defn list-admins-by-organization [organization-id db]
  (let [query (-> (h/select :oa.* [:u.name :user_name] [:u.username :user_username] [:u.position :user_position] [:u.avatar_filename :avatar_filename])
                  (h/from [:OrganizationAdmins :oa])
                  (h/join [:Users :u] [:= :oa.user_id :u.id])
                  (h/where [:= :oa.organization_id organization-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.admin/db->model))))

(s/defn list-organizations-by-admin [user-id db]
  (let [query (-> (h/select :oa.* [:o.name :organization_name])
                  (h/from [:OrganizationAdmins :oa])
                  (h/join [:Organizations :o] [:= :oa.organization_id :o.id])
                  (h/where [:= :oa.user_id user-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.admin/db->model))))

(s/defn count-admins-by-organization :- s/Int
  [organization-id db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :OrganizationAdmins)
                  (h/where [:= :organization_id organization-id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        :count
        int)))

(s/defn is-user-admin-of-organization? :- s/Bool
  [user-id organization-id db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :OrganizationAdmins)
                  (h/where [:= :user_id user-id] [:= :organization_id organization-id]))
        res (jdbc/execute-one! db (hsql/format query) opts)]
    (> (int (or (:count res) 0)) 0)))
