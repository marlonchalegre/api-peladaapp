(ns api-peladaapp.db.user
  (:require
   [api-peladaapp.adapters.user :as adapter.user]
   [api-peladaapp.models.user :as models.user]
   [medley.core :as medley.core]
   [next.jdbc.sql :as sql]
   [next.jdbc :as jdbc]
  [schema.core :as s]))

(defn- affected-rows-count
  "Get the number of affected rows from a query"
  [result]
  (->  result
      vals
      first))

(s/defn find-user-by-email :- (s/maybe models.user/User)
  "Find a user by email"
  [email :- s/Str
   db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/find-by-keys conn :users {:email email}) first adapter.user/db->model)))

(s/defn find-user-by-id :- (s/maybe models.user/User)
  "Find a user in the database by id"
  [id :- s/Int
   db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/get-by-id conn :users id) adapter.user/db->model)))

(s/defn insert-user :- s/Int
  "Insert a user and return its generated id"
  [{:keys [name email password]} :- models.user/NewUser
   db]
  (with-open [conn (jdbc/get-connection (db))]
    (sql/insert! conn :users {:name name :email email :password password})
    (-> (find-user-by-email email db) :id int)))

(s/defn update-user :- s/Int
  "Update a user in the database"
  [id :- s/Int
   user :- models.user/User
   db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/update! conn
                     :users
                     (medley.core/assoc-some {} :name (:name user)
                                                 :email (:email user)
                                                 :password (:password user))
                     {:id id})
        affected-rows-count)))

(s/defn delete-user :- s/Int
  "Delete a user from the database"
  [id :- s/Int
   db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/delete! conn :users {:id id}) affected-rows-count)))

(s/defn list-users :- [models.user/User]
  "List all users in the database"
  [db offset limit]
  (with-open [conn (jdbc/get-connection (db))]
    (->> (sql/query conn ["select * from users order by id limit ? offset ?" limit offset])
         (map adapter.user/db->model))))

(s/defn count-users :- s/Int
  "Count all users in the database"
  [db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/query conn ["select count(*) as count from users"])
        first
        :count)))

(s/defn update-user-profile :- s/Int
  "Update user profile (name, email, password only) in the database"
  [id :- s/Int
   user :- models.user/User
   db]
  ;; Only update allowed fields: name, email, password
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/update! conn
                     :users
                     (medley.core/assoc-some {}
                                             :name (:name user)
                                             :email (:email user)
                                             :password (:password user))
                     {:id id})
        affected-rows-count)))

