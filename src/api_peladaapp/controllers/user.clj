(ns api-peladaapp.controllers.user
  (:require
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.user :as logic.user]
   [api-peladaapp.models.user :as models.user]
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

(s/defn update-user-profile
  "Update user profile - only allows updating name, email, and password. Score is protected."
  [profile-data :- models.user/UserProfileUpdate
   user-id :- s/Int
   db]
  (let [existing-user (db.user/find-user-by-id user-id db)]
    (if (nil? existing-user)
      (throw (ex-info nil {:type :not-found :message "User not found"}))
      (let [;; Start with existing user
            base-user existing-user
            ;; Update with new data, only if provided
            updated-user (cond-> base-user
                           (:name profile-data) (assoc :name (:name profile-data))
                           (:email profile-data) (assoc :email (:email profile-data))
                           (:password profile-data) (assoc :password (:password profile-data)))
            ;; Encrypt password if it was updated
            final-user (if (:password profile-data)
                        (logic.user/encrypt-password updated-user)
                        updated-user)]
        (db.user/update-user-profile user-id final-user db)
        (db.user/find-user-by-id user-id db)))))
