(ns api-peladaapp.db.user
  (:require
   [api-peladaapp.adapters.user :as adapter.user]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.models.user :as models.user]
   [clojure.string :as str]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn find-user-by-email :- (s/maybe models.user/User)
  "Find a user by email (case-insensitive)"
  [email :- (s/maybe s/Str)
   db]
  (when (and email (not (str/blank? email)))
    (let [query (-> (h/select :*)
                    (h/from :Users)
                    (h/where [:= [:lower :email] (str/lower-case email)]))]
      (-> (jdbc/execute! db (hsql/format query) hsql/opts)
          first
          adapter.user/db->model))))

(s/defn find-user-by-identifier :- (s/maybe models.user/User)
  "Find a user by email or username (case-insensitive)"
  [identifier :- (s/maybe s/Str)
   db]
  (when (and identifier (not (str/blank? identifier)))
    (let [query (-> (h/select :*)
                    (h/from :Users)
                    (h/where [:or [:= [:lower :email] (str/lower-case identifier)]
                              [:= [:lower :username] (str/lower-case identifier)]]))]
      (-> (jdbc/execute! db (hsql/format query) hsql/opts)
          first
          adapter.user/db->model))))

(s/defn find-user-by-id :- (s/maybe models.user/User)
  "Find a user by id"
  [id :- s/Uuid
   db]
  (let [query (-> (h/select :*)
                  (h/from :Users)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute! db (hsql/format query) hsql/opts)
        first
        adapter.user/db->model)))

(s/defn insert-user :- s/Uuid
  "Insert a user and return its generated id"
  [user :- models.user/NewUser
   db]
  (let [{:keys [name username email password position phone]} user
        row (cond-> {:name name :username username :email email :password password :phone phone}
              position (assoc :position [:cast position :player_position])
              (contains? user :receive-non-mensalista-updates) (assoc :receive_non_mensalista_updates (boolean (:receive-non-mensalista-updates user))))
        query (-> (h/insert-into :Users)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))


(s/defn insert-partial-user :- s/Uuid
  "Insert a user with only some fields (e.g. name or email) and return its generated id"
  [fields :- {s/Keyword s/Any}
   db]
  (let [query (-> (h/insert-into :Users)
                  (h/values [fields])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))

(s/defn insert-guest-user :- s/Uuid
  "Insert a user with only name (for guests)"
  [name :- s/Str db]
  (insert-partial-user {:name name} db))

(s/defn insert-user-by-name :- s/Uuid
  "Insert a user with only name and return its generated id"
  [name :- s/Str db]
  (insert-partial-user {:name name} db))

(s/defn update-user :- s/Int
  "Update a user in the database"
  [id :- s/Uuid
   user :- models.user/User
   db]
  (let [row (cond-> {:name (:name user)
                     :username (:username user)
                     :email (:email user)
                     :password (:password user)
                     :phone (:phone user)}
              (:position user) (assoc :position [:cast (:position user) :player_position])
              (contains? user :receive-non-mensalista-updates) (assoc :receive_non_mensalista_updates (boolean (:receive-non-mensalista-updates user))))
        query (-> (h/update :Users)
                  (h/set row)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn update-password :- s/Int
  "Update a user's password in the database"
  [id :- s/Uuid
   password :- s/Str
   db]
  (let [query (-> (h/update :Users)
                  (h/set {:password password})
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn update-user-profile :- s/Int
  "Update only specific fields of a user's profile"
  [id :- s/Uuid
   user :- s/Any
   db]
  (let [row (cond-> {:name (:name user)
                     :username (:username user)
                     :email (:email user)
                     :phone (:phone user)
                     :avatar_filename (:avatar-filename user)}
              (:position user) (assoc :position [:cast (:position user) :player_position])
              (contains? user :receive-non-mensalista-updates) (assoc :receive_non_mensalista_updates (boolean (:receive-non-mensalista-updates user))))
        query (-> (h/update :Users)
                  (h/set row)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn list-opted-in-non-mensalista-users-by-org :- [models.user/User]
  "List users in an organization with member_type diarista or convidado who opted in to non-mensalista updates"
  [org-id :- s/Uuid db]
  (let [query (-> (h/select-distinct :u.*)
                  (h/from [:Users :u])
                  (h/join [:OrganizationPlayers :op] [:= :op.user_id :u.id])
                  (h/where [:= :op.organization_id org-id]
                           [:in :op.member_type [[:cast "diarista" :member_type]
                                                 [:cast "convidado" :member_type]
                                                 [:cast "diarista_temporario" :member_type]]]
                           [:= :u.receive_non_mensalista_updates true]
                           [:!= :u.phone nil]
                           [:!= :u.phone ""]))]
    (map adapter.user/db->model (jdbc/execute! db (hsql/format query) hsql/opts))))


(s/defn get-users-by-ids :- [models.user/User]
  "Get users by a list of ids"
  [db ids :- [s/Uuid]]
  (if (empty? ids)
    []
    (let [query (-> (h/select :*)
                    (h/from :Users)
                    (h/where [:in :id ids]))]
      (->> (jdbc/execute! db (hsql/format query) hsql/opts)
           (map adapter.user/db->model)))))

(s/defn delete-user :- s/Int
  "Delete a user from the database"
  [id :- s/Uuid
   db]
  (let [query (-> (h/delete-from :Users)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn list-users :- [models.user/User]
  "List all users in the database"
  [db offset limit]
  (let [query (-> (h/select :*)
                  (h/from :Users)
                  (h/order-by [:id :asc])
                  (h/limit limit)
                  (h/offset offset))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map adapter.user/db->model))))

(s/defn count-users :- s/Int
  "Count all users in the database"
  [db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Users))]
    (int (:count (jdbc/execute-one! db (hsql/format query) hsql/opts)))))

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
    (->> (jdbc/execute! db (hsql/format hsql-query) hsql/opts)
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
    (int (:count (jdbc/execute-one! db (hsql/format hsql-query) hsql/opts)))))

(s/defn find-user-by-username :- (s/maybe models.user/User)
  "Find a user by username (case-insensitive)"
  [username :- (s/maybe s/Str)
   db]
  (when (and username (not (str/blank? username)))
    (let [query (-> (h/select :*)
                    (h/from :Users)
                    (h/where [:= [:lower :username] (str/lower-case username)]))]
      (-> (jdbc/execute! db (hsql/format query) hsql/opts)
          first
          adapter.user/db->model))))

(s/defn search-users-in-shared-orgs :- [models.user/User]
  "Search users that share at least one organization with the current user"
  [db current-user-id :- s/Uuid query offset limit]
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
    (->> (jdbc/execute! db (hsql/format hsql-query) hsql/opts)
         (map adapter.user/db->model))))

(s/defn count-searched-users-in-shared-orgs :- s/Int
  "Count users matching the search query within shared organizations"
  [db current-user-id :- s/Uuid query]
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
    (int (:count (jdbc/execute-one! db (hsql/format hsql-query) hsql/opts)))))

(s/defn count-users-in-organization :- s/Int
  "Count users in a specific organization"
  [organization-id :- s/Uuid
   db]
  (let [query (-> (h/select [[:count :*] :total])
                  (h/from :Users)
                  (h/join [:OrganizationPlayers :op] [:= :op.user_id :Users.id])
                  (h/where [:= :op.organization_id organization-id]))]
    (int (:total (jdbc/execute-one! db (hsql/format query) hsql/opts)))))

(s/defn list-users-in-organization :- [[s/Any]]
  "List users in a specific organization with pagination"
  [organization-id :- s/Uuid
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
    [(jdbc/execute! db (hsql/format query) hsql/opts)
     (:total (jdbc/execute-one! db (hsql/format total-count-query) hsql/opts))]))

(s/defn update-user-flags :- s/Int
  "Update user flags in the database"
  [id :- s/Uuid
   flags :- {(s/optional-key :is_super_admin) s/Bool
             (s/optional-key :is_blocked) s/Bool
             (s/optional-key :allow_org_creation) s/Bool}
   db]
  (let [query (-> (h/update :Users)
                  (h/set flags)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn get-user-stats
  [user-id :- s/Uuid
   db]
  (let [user-uuid [:cast user-id :uuid]
        current-year (.getYear (java.time.LocalDate/now))
        year-str (str current-year)
        ;; 1. Goals and assists from PeladaPlayerStats
        pelada-stats-query (-> (h/select [[:coalesce [:sum :ps.goals] 0] :goals]
                                         [[:coalesce [:sum :ps.assists] 0] :assists])
                               (h/from [:PeladaPlayerStats :ps])
                               (h/join [:OrganizationPlayers :op] [:= :ps.player_id :op.id])
                               (h/join [:Peladas :p] [:= :ps.pelada_id :p.id])
                               (h/where [:and
                                         [:= :op.user_id user-uuid]
                                         [:= [:to_char :p.scheduled_at "YYYY"] year-str]]))
        pelada-stats (jdbc/execute-one! db (hsql/format pelada-stats-query) hsql/opts)

        ;; 2. Goals and assists from ManualStats
        manual-stats-query (-> (h/select [[:coalesce [:sum :ms.goals] 0] :goals]
                                         [[:coalesce [:sum :ms.assists] 0] :assists])
                               (h/from [:ManualStats :ms])
                               (h/join [:OrganizationPlayers :op] [:= :ms.player_id :op.id])
                               (h/where [:and
                                         [:= :op.user_id user-uuid]
                                         [:= :ms.year current-year]]))
        manual-stats (jdbc/execute-one! db (hsql/format manual-stats-query) hsql/opts)

        ;; 3. Attendance confirmed / matches played.
        attendance-query (-> (h/select [[:count [:distinct :a.pelada_id]] :count])
                             (h/from [:Attendance :a])
                             (h/join [:OrganizationPlayers :op] [:= :a.player_id :op.id])
                             (h/join [:Peladas :p] [:= :a.pelada_id :p.id])
                             (h/where [:and
                                       [:= :op.user_id user-uuid]
                                       [:= :a.status [:cast "confirmed" :attendance_status]]
                                       [:= :p.status [:cast "closed" :pelada_status]]
                                       [:= [:to_char :p.scheduled_at "YYYY"] year-str]]))
        attendance-count (:count (jdbc/execute-one! db (hsql/format attendance-query) hsql/opts))]
    {:goals (+ (int (or (:goals pelada-stats) 0))
               (int (or (:goals manual-stats) 0)))
     :assists (+ (int (or (:assists pelada-stats) 0))
                 (int (or (:assists manual-stats) 0)))
     :matches (int (or attendance-count 0))}))


