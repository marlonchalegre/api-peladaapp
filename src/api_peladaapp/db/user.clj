(ns api-peladaapp.db.user
  (:require
   [api-peladaapp.adapters.user :as adapter.user]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.models.user :as models.user]
   [clojure.string :as str]
   [honey.sql.helpers :as h]
   [medley.core :as medley.core]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn find-user-by-email :- (s/maybe models.user/User)
  "Find a user by email (case-insensitive)"
  [email :- (s/maybe s/Str)
   db]
  (when email
    (let [query (-> (h/select :*)
                    (h/from :Users)
                    (h/where [:= [:lower :email] (str/lower-case email)]))]
      (-> (jdbc/execute! db (hsql/format query) opts)
          first
          adapter.user/db->model))))

(s/defn find-user-by-identifier :- (s/maybe models.user/User)
  "Find a user by email or username (case-insensitive)"
  [identifier :- (s/maybe s/Str)
   db]
  (when identifier
    (let [query (-> (h/select :*)
                    (h/from :Users)
                    (h/where [:or [:= [:lower :email] (str/lower-case identifier)]
                              [:= [:lower :username] (str/lower-case identifier)]]))]
      (-> (jdbc/execute! db (hsql/format query) opts)
          first
          adapter.user/db->model))))

(s/defn find-user-by-id :- (s/maybe models.user/User)
  "Find a user by id"
  [id :- s/Int
   db]
  (let [query (-> (h/select :*)
                  (h/from :Users)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute! db (hsql/format query) opts)
        first
        adapter.user/db->model)))

(s/defn insert-user :- s/Int
  "Insert a user and return its generated id"
  [{:keys [name username email password position phone]} :- models.user/NewUser
   db]
  (let [row (medley.core/assoc-some {} :name name :username username :email email :password password :position position :phone phone)
        query (-> (h/insert-into :Users)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn insert-guest-user :- s/Int
  "Insert a user with only name (for guests)"
  [name :- s/Str
   db]
  (let [query (-> (h/insert-into :Users)
                  (h/values [{:name name}])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn insert-partial-user :- s/Int
  "Insert a user with only email (for invitations)"
  [email :- s/Str
   db]
  (let [query (-> (h/insert-into :Users)
                  (h/values [{:email email}])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn insert-user-by-name :- s/Int
  "Insert a user with only name and return its generated id"
  [name :- s/Str
   db]
  (let [query (-> (h/insert-into :Users)
                  (h/values [{:name name}])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn update-user :- s/Int
  "Update a user in the database"
  [id :- s/Int
   user :- models.user/User
   db]
  (let [row (medley.core/assoc-some {}
                                    :name (:name user)
                                    :username (:username user)
                                    :email (:email user)
                                    :password (:password user)
                                    :position (:position user)
                                    :phone (:phone user))
        query (-> (h/update :Users)
                  (h/set row)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn update-user-profile :- s/Int
  "Update only specific fields of a user's profile"
  [id :- s/Int
   user :- s/Any
   db]
  (let [row (medley.core/assoc-some {}
                                    :name (:name user)
                                    :username (:username user)
                                    :email (:email user)
                                    :position (:position user)
                                    :phone (:phone user)
                                    :avatar_filename (:avatar-filename user))
        query (-> (h/update :Users)
                  (h/set row)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn get-users-by-ids :- [models.user/User]
  "Get users by a list of ids"
  [db ids]
  (if (empty? ids)
    []
    (let [query (-> (h/select :*)
                    (h/from :Users)
                    (h/where [:in :id ids]))]
      (->> (jdbc/execute! db (hsql/format query) opts)
           (map adapter.user/db->model)))))

(s/defn delete-user :- s/Int
  "Delete a user from the database"
  [id :- s/Int
   db]
  (let [query (-> (h/delete-from :Users)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn list-users :- [models.user/User]
  "List all users in the database"
  [db offset limit]
  (let [query (-> (h/select :*)
                  (h/from :Users)
                  (h/order-by [:id :asc])
                  (h/limit limit)
                  (h/offset offset))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.user/db->model))))

(s/defn count-users :- s/Int
  "Count all users in the database"
  [db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Users))]
    (int (:count (jdbc/execute-one! db (hsql/format query) opts)))))

(s/defn search-users :- [models.user/User]
  "Search users by name, username or email with pagination"
  [db query offset limit]
  (let [lower-pattern (str "%" (str/lower-case query) "%")
        hsql-query (-> (h/select :*)
                       (h/from :Users)
                       (h/where [:or [[:like [:lower :name] lower-pattern]]
                                 [[:like [:lower :username] lower-pattern]]
                                 [[:like [:lower :email] lower-pattern]]])
                       (h/order-by :name)
                       (h/limit limit)
                       (h/offset offset))]
    (->> (jdbc/execute! db (hsql/format hsql-query) opts)
         (map adapter.user/db->model))))

(s/defn count-searched-users :- s/Int
  "Count users matching the search query"
  [db query]
  (let [lower-pattern (str "%" (str/lower-case query) "%")
        hsql-query (-> (h/select [[:count :*] :count])
                       (h/from :Users)
                       (h/where [:or [[:like [:lower :name] lower-pattern]]
                                 [[:like [:lower :username] lower-pattern]]
                                 [[:like [:lower :email] lower-pattern]]]))]
    (int (:count (jdbc/execute-one! db (hsql/format hsql-query) opts)))))

(s/defn find-user-by-username :- (s/maybe models.user/User)
  "Find a user by username (case-insensitive)"
  [username :- s/Str
   db]
  (when username
    (let [query (-> (h/select :*)
                    (h/from :Users)
                    (h/where [:= [:lower :username] (str/lower-case username)]))]
      (-> (jdbc/execute! db (hsql/format query) opts)
          first
          adapter.user/db->model))))

(s/defn search-users-in-shared-orgs :- [models.user/User]
  "Search users that share at least one organization with the current user"
  [db current-user-id query offset limit]
  (let [lower-pattern (str "%" (str/lower-case query) "%")
        hsql-query (-> (h/select-distinct :u.*)
                       (h/from [:Users :u])
                       (h/left-join [:OrganizationPlayers :op] [:= :u.id :op.user_id])
                       (h/left-join [:OrganizationAdmins :oa] [:= :u.id :oa.user_id])
                       (h/where [:and
                                 [:or [[:like [:lower :u.name] lower-pattern]]
                                  [[:like [:lower :u.username] lower-pattern]]
                                  [[:like [:lower :u.email] lower-pattern]]]
                                 [:or
                                  [:in :op.organization_id (-> (h/select :organization_id) (h/from :OrganizationPlayers) (h/where [:= :user_id current-user-id]))]
                                  [:in :op.organization_id (-> (h/select :organization_id) (h/from :OrganizationAdmins) (h/where [:= :user_id current-user-id]))]
                                  [:in :oa.organization_id (-> (h/select :organization_id) (h/from :OrganizationPlayers) (h/where [:= :user_id current-user-id]))]
                                  [:in :oa.organization_id (-> (h/select :organization_id) (h/from :OrganizationAdmins) (h/where [:= :user_id current-user-id]))]]])
                       (h/order-by :u.name)
                       (h/limit limit)
                       (h/offset offset))]
    (->> (jdbc/execute! db (hsql/format hsql-query) opts)
         (map adapter.user/db->model))))

(s/defn count-searched-users-in-shared-orgs :- s/Int
  "Count users matching the search query within shared organizations"
  [db current-user-id query]
  (let [lower-pattern (str "%" (str/lower-case query) "%")
        hsql-query (-> (h/select [[:count [:distinct :u.id]] :count])
                       (h/from [:Users :u])
                       (h/left-join [:OrganizationPlayers :op] [:= :u.id :op.user_id])
                       (h/left-join [:OrganizationAdmins :oa] [:= :u.id :oa.user_id])
                       (h/where [:and
                                 [:or [[:like [:lower :u.name] lower-pattern]]
                                  [[:like [:lower :u.username] lower-pattern]]
                                  [[:like [:lower :u.email] lower-pattern]]]
                                 [:or
                                  [:in :op.organization_id (-> (h/select :organization_id) (h/from :OrganizationPlayers) (h/where [:= :user_id current-user-id]))]
                                  [:in :op.organization_id (-> (h/select :organization_id) (h/from :OrganizationAdmins) (h/where [:= :user_id current-user-id]))]
                                  [:in :oa.organization_id (-> (h/select :organization_id) (h/from :OrganizationPlayers) (h/where [:= :user_id current-user-id]))]
                                  [:in :oa.organization_id (-> (h/select :organization_id) (h/from :OrganizationAdmins) (h/where [:= :user_id current-user-id]))]]]))]
    (int (:count (jdbc/execute-one! db (hsql/format hsql-query) opts)))))

(s/defn count-users-in-organization :- s/Int
  "Count users in a specific organization"
  [organization-id :- s/Int
   db]
  (let [query (-> (h/select [[:count :*] :total])
                  (h/from :Users)
                  (h/join [:OrganizationPlayers :op] [:= :op.user_id :Users.id])
                  (h/where [:= :op.organization_id organization-id]))]
    (int (:total (jdbc/execute-one! db (hsql/format query) opts)))))

(s/defn list-users-in-organization :- [[s/Any]]
  "List users in a specific organization with pagination"
  [organization-id :- s/Int
   offset :- s/Int
   per-page :- s/Int
   db]
  (let [query (-> (h/select :*)
                  (h/from :Users)
                  (h/join [:OrganizationPlayers :op] [:= :op.user_id :Users.id])
                  (h/where [:= :op.organization_id organization-id])
                  (h/order-by [:id :asc])
                  (h/limit per-page)
                  (h/offset offset))
        total-count-query (-> (h/select [[:count :*] :total])
                              (h/from :Users)
                              (h/join [:OrganizationPlayers :op] [:= :op.user_id :Users.id])
                              (h/where [:= :op.organization_id organization-id]))]
    [(jdbc/execute! db (hsql/format query) opts)
     (:total (jdbc/execute-one! db (hsql/format total-count-query) opts))]))