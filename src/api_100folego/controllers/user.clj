(ns api-100folego.controllers.user
  (:require
   [api-100folego.db.user :as db.user]
   [api-100folego.logic.user :as logic.user]
   [api-100folego.models.user :as models.user]
   [schema.core :as s]))

(s/defn create-user
  [user :- models.user/NewUser
   db]
  (let [user? (-> (db.user/find-user-by-email (:email user) db)
                  some?)]
    (if user?
      (throw (ex-info nil {:type :already-exist :message "User already exists"}))
      (as-> user $
        (logic.user/encrypt-password $)
        (db.user/insert-user $ db)
        (assoc user :id $)))))

(s/defn update-user
  [user :- models.user/User
   user-id :- s/Int
   db]
  (let [existing-user (-> (db.user/find-user-by-id user-id db)
                          (dissoc :id :password))]
    (if (nil? existing-user)
      (throw (ex-info nil {:type :not-found :message "User not found"}))
       (as-> user $
          (merge existing-user $)
          (logic.user/encrypt-password $)
          (do (db.user/update-user user-id $ db)
              (db.user/find-user-by-id user-id db))))))

(s/defn get-user :- models.user/User
  [user-id :- s/Int
   db]
  (let [user (db.user/find-user-by-id user-id db)]
    (if (nil? user)
      (throw (ex-info nil {:type :not-found :message "User not found"}))
      user)))

(s/defn delete-user
  [user-id :- s/Int
   db]
  (let [user (db.user/find-user-by-id user-id db)]
    (if (nil? user)
      (throw (ex-info nil {:type :not-found :message "User not found"}))
      (db.user/delete-user user-id db))))

(s/defn list-users :- [models.user/User]
  [db]
  (map #(dissoc % :password) (db.user/list-users db)))
