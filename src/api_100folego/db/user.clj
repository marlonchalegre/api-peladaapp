(ns api-100folego.db.user
  (:require
   [api-100folego.adapters.user :as adapter.user]
   [api-100folego.models.user :as models.user]
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
  "Find a user by email"
  [email :- s/Str
   db]
  (-> (sql/find-by-keys (db) :users {:email email}) first adapter.user/db->model))

(s/defn find-user-by-id :- (s/maybe models.user/User)
  "Find a user in the database by id"
  [id :- s/Int
   db]
  (-> (sql/get-by-id (db) :users id) adapter.user/db->model))

(s/defn insert-user :- s/Int
  "Insert a user and return its generated id"
  [{:keys [name email password]} :- models.user/NewUser
   db]
  (do
    (sql/insert! (db) :users {:name name :email email :password password})
    (-> (find-user-by-email email db) :id int)))

(s/defn update-user :- s/Int
  "Update a user in the database"
  [id :- s/Int
   user :- models.user/User
   db]
  (-> (sql/update! (db)
                   :users
                   (medley.core/assoc-some {} :name (:name user)
                                               :email (:email user)
                                               :password (:password user))
                   {:id id})
      affected-rows-count))

(s/defn delete-user :- s/Int
  "Delete a user from the database"
  [id :- s/Int
   db]
  (-> (sql/delete! (db) :users {:id id}) affected-rows-count))

(s/defn list-users :- [models.user/User]
  "List all users in the database"
  [db]
  (->> (sql/query (db) ["select * from users"]) (map adapter.user/db->model)))

