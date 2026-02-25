(ns api-peladaapp.db.user
  (:require
   [api-peladaapp.adapters.user :as adapter.user]
   [api-peladaapp.models.user :as models.user]
   [clojure.string :as str]
   [medley.core :as medley.core]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count
  "Get the number of affected rows from a query"
  [result]
  (->  result
       vals
       first))

(s/defn find-user-by-email :- (s/maybe models.user/User)
  "Find a user by email (case-insensitive)"
  [email :- (s/maybe s/Str)
   db]
  (when email
    (-> (sql/query db ["SELECT * FROM users WHERE LOWER(email) = LOWER(?)" email]) first adapter.user/db->model)))

(s/defn find-user-by-username :- (s/maybe models.user/User)
  "Find a user by username (case-insensitive)"
  [username :- (s/maybe s/Str)
   db]
  (when username
    (-> (sql/query db ["SELECT * FROM users WHERE LOWER(username) = LOWER(?)" username]) first adapter.user/db->model)))

(s/defn find-user-by-identifier :- (s/maybe models.user/User)
  "Find a user by email or username (case-insensitive)"
  [identifier :- (s/maybe s/Str)
   db]
  (when identifier
    (-> (sql/query db ["SELECT * FROM users WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?)" identifier identifier]) first adapter.user/db->model)))

(s/defn find-user-by-id :- (s/maybe models.user/User)
  "Find a user in the database by id"
  [id :- s/Int
   db]
  (-> (sql/get-by-id db :users id) adapter.user/db->model))

(s/defn insert-user :- s/Int
  "Insert a user and return its generated id"
  [{:keys [name username email password position]} :- models.user/NewUser
   db]
  (-> (sql/insert! db :users (medley.core/assoc-some {} :name name :username username :email email :password password :position position))
      affected-rows-count
      int))

(s/defn insert-partial-user :- s/Int
  "Insert a user with only email and return its generated id"
  [email :- s/Str
   db]
  (-> (sql/insert! db :users {:email email})
      affected-rows-count
      int))

(s/defn insert-user-by-name :- s/Int
  "Insert a user with only name and return its generated id"
  [name :- s/Str
   db]
  (-> (sql/insert! db :users {:name name})
      affected-rows-count
      int))

(s/defn update-user :- s/Int
  "Update a user in the database"
  [id :- s/Int
   user :- models.user/User
   db]
  (-> (sql/update! db
                   :users
                   (medley.core/assoc-some {} :name (:name user)
                                           :username (:username user)
                                           :email (:email user)
                                           :password (:password user)
                                           :position (:position user))
                   {:id id})
      affected-rows-count))

(s/defn delete-user :- s/Int
  "Delete a user from the database"
  [id :- s/Int
   db]
  (-> (sql/delete! db :users {:id id}) affected-rows-count))

(s/defn list-users :- [models.user/User]
  "List all users in the database"
  [db offset limit]
  (->> (sql/query db ["select * from users order by id limit ? offset ?" limit offset])
       (map adapter.user/db->model)))

(s/defn count-users :- s/Int
  "Count all users in the database"
  [db]
  (-> (sql/query db ["select count(*) as count from users"])
      first
      :count))

(s/defn search-users :- [models.user/User]
  "Search users by name, username or email with pagination"
  [db query offset limit]
  (let [q (str "%" (str/lower-case query) "%")]
    (->> (sql/query db ["SELECT * FROM users WHERE LOWER(name) LIKE ? OR LOWER(username) LIKE ? OR LOWER(email) LIKE ? ORDER BY name LIMIT ? OFFSET ?" q q q limit offset])
         (map adapter.user/db->model))))

(s/defn count-searched-users :- s/Int
  "Count users matching the search query"
  [db query]
  (let [q (str "%" (str/lower-case query) "%")]
    (-> (sql/query db ["SELECT count(*) as count FROM users WHERE LOWER(name) LIKE ? OR LOWER(username) LIKE ? OR LOWER(email) LIKE ?" q q q])
        first
        :count)))

(s/defn update-user-profile :- s/Int
  "Update user profile (name, username, email, password, position only) in the database"
  [id :- s/Int
   user :- models.user/User
   db]
  ;; Only update allowed fields: name, username, email, password, position
  (-> (sql/update! db
                   :users
                   (medley.core/assoc-some {}
                                           :name (:name user)
                                           :username (:username user)
                                           :email (:email user)
                                           :password (:password user)
                                           :position (:position user))
                   {:id id})
      affected-rows-count))
