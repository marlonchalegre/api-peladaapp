(ns api-peladaapp.controllers.auth
  (:require
   [api-peladaapp.config :as config]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization-invitation :as db.invitation]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.user :as logic.user]
   [api-peladaapp.models.credential :as models.credential]
   [api-peladaapp.models.user :as models.user]
   [buddy.hashers :as hashers]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
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
  [{:keys [email username name password position phone token]} :- models.user/NewUser
   db]
  (let [invitation (db.invitation/get-invitation-by-token token db)
        user-db (db.user/find-user-by-identifier (or email username) db)]
    (cond
      (nil? invitation)
      (throw (ex-info "Invalid or expired invitation token."
                      {:type :bad-request :message "Invalid or expired invitation token."}))

      (not= (:status invitation) "pending")
      (throw (ex-info "Invitation already used."
                      {:type :bad-request :message "Invitation already used."}))

      (nil? user-db)
      (throw (ex-info "User not found. Please ask for an invitation first."
                      {:type :not-found :message "User not found"}))

      ;; Security check: invitation must belong to this user (email or guest-ID)
      (let [identifier (:email invitation)]
        (and identifier
             (not (str/starts-with? identifier "guest-"))
             (not= identifier (:email user-db))
             (not= identifier (:username user-db))))
      (throw (ex-info "This invitation does not belong to this account."
                      {:type :forbidden :message "This invitation belongs to another user."}))

      (some? (:password user-db))
      (throw (ex-info "User already has a password. Please use login."
                      {:type :already-exist :message "User already registered"}))

      :else
      (jdbc/with-transaction [tx db]
        (let [updated-user (as-> {:email email :username username :name name :password password :position position :phone phone} $
                             (logic.user/encrypt-password $)
                             (do (db.user/update-user (:id user-db) $ tx)
                                 (db.user/find-user-by-id (:id user-db) tx)))
              secret (config/get-key :jwt-secret)
              admin-orgs (map :organization-id (db.admin/list-organizations-by-admin (:id user-db) tx))
              user-with-orgs (assoc updated-user :admin-orgs admin-orgs)]
          ;; Mark invitation as accepted and join organization
          (db.invitation/update-invitation-status (:id invitation) "accepted" tx)
          (let [org-id (:organization-id invitation)]
            (when-not (db.player/get-org-player-by-user-id (:id user-db) org-id tx)
              (jdbc/execute! tx ["INSERT INTO OrganizationPlayers (user_id, organization_id, grade, member_type) VALUES (?, ?, 5.0, 'convidado')"
                                 (:id user-db) org-id])))
          {:token (logic.user/build-token user-with-orgs secret)
           :user user-with-orgs})))))