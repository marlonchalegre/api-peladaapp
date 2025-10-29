(ns api-100folego.controllers.auth
  (:require
   [api-100folego.config :as config]
   [api-100folego.db.user :as db.user]
   [api-100folego.logic.user :as logic.user]
   [api-100folego.models.credential :as models.credential]
   [buddy.hashers :as hashers]
   [schema.core :as s]))

(s/defn authenticate :- s/Str
  "Authenticate a user by email/password and return a JWT token."
  [{:keys [email password]} :- models.credential/Credential
   db]
  (let [user-db (db.user/find-user-by-email email db)
        secret (config/get-key :jwt-secret)
        db-pass (:password user-db)]
    (when (nil? user-db)
      (throw (ex-info nil {:type :not-found :message "User not found"})))
    (when (or (nil? password) (nil? db-pass) (not (hashers/check password db-pass)))
      (throw (ex-info nil {:type :invalid-credentials :message "Invalid credentials"})))
    (logic.user/build-token user-db secret)))
