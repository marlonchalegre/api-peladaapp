(ns api-peladaapp.controllers.auth
  (:require
   [api-peladaapp.config :as config]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.user :as logic.user]
   [api-peladaapp.models.credential :as models.credential]
   [api-peladaapp.models.user :as models.user]
   [buddy.hashers :as hashers]
   [schema.core :as s]))

(s/defn authenticate :- {:token s/Str :user models.user/User}
  "Authenticate a user by email/username and password and return a JWT token with user info."
  [{:keys [email password]} :- models.credential/Credential
   db]
  (let [user-db (db.user/find-user-by-identifier email db)
        secret (config/get-key :jwt-secret)
        db-pass (:password user-db)]
    (when (nil? user-db)
      (throw (ex-info nil {:type :invalid-credentials :message "Invalid credentials"})))
    (when (or (nil? password) (nil? db-pass) (not (hashers/check password db-pass)))
      (throw (ex-info nil {:type :invalid-credentials :message "Invalid credentials"})))
    (let [admin-orgs (map :organization-id (db.admin/list-organizations-by-admin (:id user-db) db))
          user-with-orgs (assoc user-db :admin-orgs admin-orgs)]
      {:token (logic.user/build-token user-with-orgs secret)
       :user user-with-orgs})))

(s/defn first-access :- {:token s/Str :user models.user/User}
  "Complete registration for an invited user (who has no password set)."
  [{:keys [email username name password position phone]} :- models.user/NewUser
   db]
  (let [user-db (db.user/find-user-by-identifier (or email username) db)]
    (cond
      (nil? user-db)
      (throw (ex-info "User not found. Please ask for an invitation first."
                      {:type :not-found :message "User not found"}))

      (some? (:password user-db))
      (throw (ex-info "User already has a password. Please use login."
                      {:type :already-exist :message "User already registered"}))

      :else
      (let [updated-user (as-> {:email email :username username :name name :password password :position position :phone phone} $
                           (logic.user/encrypt-password $)
                           (do (db.user/update-user (:id user-db) $ db)
                               (db.user/find-user-by-id (:id user-db) db)))
            secret (config/get-key :jwt-secret)
            admin-orgs (map :organization-id (db.admin/list-organizations-by-admin (:id user-db) db))
            user-with-orgs (assoc updated-user :admin-orgs admin-orgs)]
        {:token (logic.user/build-token user-with-orgs secret)
         :user user-with-orgs}))))