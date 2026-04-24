(ns api-peladaapp.controllers.user
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.logic.user :as logic.user]
   [api-peladaapp.models.user :as models.user]
   [schema.core :as s]))

(s/defn create-user :- models.user/User
  [user :- models.user/NewUser
   db]
  (let [username (:username user)
        email (:email user)
        existing-user-username (when username (db.user/find-user-by-username username db))
        existing-user-email (when email (db.user/find-user-by-email email db))]
    (cond
      ;; Username already taken
      (and existing-user-username (:password existing-user-username))
      (throw (ex-info "Username already exists" {:type :already-exist :message "Username already exists"}))

      ;; Email already taken
      (and existing-user-email (:password existing-user-email))
      (throw (ex-info "Email already exists" {:type :already-exist :message "Email already exists"}))

      ;; User exists but has no password (partial) -> Update/Claim
      ;; We prefer matching by email for partial users if available
      (or existing-user-username existing-user-email)
      (let [existing-user (or existing-user-email existing-user-username)]
        (as-> user $
          (logic.user/encrypt-password $)
          (do (db.user/update-user (:id existing-user) $ db)
              (assoc user :id (:id existing-user)))))

      ;; User does not exist -> Insert
      :else
      (as-> user $
        (logic.user/encrypt-password $)
        (db.user/insert-user $ db)
        (assoc user :id $)))))

(s/defn update-user :- models.user/User
  [user :- models.user/UserProfileUpdate
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
      (let [admin-orgs (map :organization-id (db.admin/list-organizations-by-admin user-id db))]
        (assoc user :admin-orgs admin-orgs)))))

(s/defn delete-user
  [user-id :- s/Int
   db]
  (let [user (db.user/find-user-by-id user-id db)]
    (if (nil? user)
      (throw (ex-info nil {:type :not-found :message "User not found"}))
      (db.user/delete-user user-id db))))

(s/defn list-users
  [db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        users (db.user/list-users db offset per-page)
        total-count (db.user/count-users db)]
    (pagination/with-pagination-headers users total-count page per-page)))

(s/defn search-users
  [db query pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        users (db.user/search-users db query offset per-page)
        total-count (db.user/count-searched-users db query)]
    (pagination/with-pagination-headers users total-count page per-page)))

(s/defn search-users-in-shared-orgs
  [db current-user-id query pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        users (db.user/search-users-in-shared-orgs db current-user-id query offset per-page)
        total-count (db.user/count-searched-users-in-shared-orgs db current-user-id query)]
    (pagination/with-pagination-headers users total-count page per-page)))

(s/defn update-user-profile :- models.user/User
  "Update user profile - only allows updating name, username, email, password and position. Score is protected."
  [profile-data :- models.user/UserProfileUpdate
   user-id :- s/Int
   db]
  (let [existing-user (db.user/find-user-by-id user-id db)]
    (if (nil? existing-user)
      (throw (ex-info nil {:type :not-found :message "User not found"}))
      (let [;; Start with existing user
            base-user existing-user

            ;; Check for username uniqueness if provided and different from existing
            new-username (:username profile-data)
            _ (when (and new-username
                         (not= new-username (:username base-user))
                         (db.user/find-user-by-username new-username db))
                (throw (ex-info "Username already exists" {:type :already-exist :message "Username already exists"})))

            ;; Check for email uniqueness if provided and different from existing
            new-email (:email profile-data)
            _ (when (and new-email
                         (not= new-email (:email base-user))
                         (db.user/find-user-by-email new-email db))
                (throw (ex-info "Email already exists" {:type :already-exist :message "Email already exists"})))

            ;; Update with new data, only if provided
            updated-user (cond-> base-user
                           (:name profile-data) (assoc :name (:name profile-data))
                           (:username profile-data) (assoc :username (:username profile-data))
                           (:email profile-data) (assoc :email (:email profile-data))
                           (:password profile-data) (assoc :password (:password profile-data))
                           (:position profile-data) (assoc :position (:position profile-data))
                           (contains? profile-data :phone) (assoc :phone (:phone profile-data))
                           (contains? profile-data :avatar-filename) (assoc :avatar-filename (:avatar-filename profile-data)))
            ;; Encrypt password if it was updated
            final-user (if (:password profile-data)
                         (logic.user/encrypt-password updated-user)
                         updated-user)]
        (db.user/update-user-profile user-id final-user db)
        (let [admin-orgs (map :organization-id (db.admin/list-organizations-by-admin user-id db))]
          (assoc (db.user/find-user-by-id user-id db) :admin-orgs admin-orgs))))))

