(ns api-peladaapp.controllers.user
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.logic.user :as logic.user]
   [api-peladaapp.models.user :as models.user]
   [clojure.string :as str]
   [schema.core :as s]))

(s/defn create-user :- models.user/User
  [user :- models.user/NewUser
   db]
  (let [email (:email user)
        existing-user-email (when email (db.user/find-user-by-email email db))
        existing-username (when (:username user) (db.user/find-user-by-identifier (:username user) db))]
    (cond
      ;; Email already taken
      (and existing-user-email (:password existing-user-email))
      (throw (ex-info "Email already exists" {:type :already-exist :message "Email already exists"}))

      ;; Username already taken
      (and existing-username (:password existing-username))
      (throw (ex-info "Username already exists" {:type :already-exist :message "Username already exists"}))

      ;; User exists but has no password (partial) -> Update/Claim
      ;; We prefer matching by email for partial users if available
      (or existing-user-email existing-username)
      (let [existing-user (or existing-user-email existing-username)]
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
   user-id :- s/Uuid
   db]
  (let [existing-user (-> (db.user/find-user-by-id user-id db)
                          (dissoc :id :password))]
    (if (nil? existing-user)
      (throw (ex-info "User not found" {:type :not-found :message "User not found"}))
      (as-> user $
        (merge existing-user $)
        (logic.user/encrypt-password $)
        (do (db.user/update-user user-id $ db)
            (db.user/find-user-by-id user-id db))))))

(defn- enrich-user
  [user user-id db]
  (let [admin-orgs (map :organization-id (db.admin/list-organizations-by-admin user-id db))
        stats (db.user/get-user-stats user-id db)]
    (assoc user
           :admin-orgs admin-orgs
           :stats stats)))

(s/defn get-user :- models.user/User
  [user-id :- s/Uuid
   db]
  (let [user (db.user/find-user-by-id user-id db)]
    (if (nil? user)
      (throw (ex-info "User not found" {:type :not-found :message "User not found"}))
      (enrich-user user user-id db))))

(s/defn delete-user
  [user-id :- s/Uuid
   db]
  (let [user (db.user/find-user-by-id user-id db)]
    (if (nil? user)
      (throw (ex-info "User not found" {:type :not-found :message "User not found"}))
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
   user-id :- s/Uuid
   db]
  (let [existing-user (db.user/find-user-by-id user-id db)]
    (if (nil? existing-user)
      (throw (ex-info "User not found" {:type :not-found :message "User not found"}))
      (let [base-user existing-user
            new-username (:username profile-data)
            _ (when (and new-username
                         (not (str/blank? new-username)))
                (when-let [existing (db.user/find-user-by-username new-username db)]
                  (when (not= (:id existing) user-id)
                    (throw (ex-info "Username already exists" {:type :already-exist :message "Username already exists"})))))

            new-email (:email profile-data)
            _ (when (and new-email
                         (not (str/blank? new-email)))
                (when-let [existing (db.user/find-user-by-email new-email db)]
                  (when (not= (:id existing) user-id)
                    (throw (ex-info "Email already exists" {:type :already-exist :message "Email already exists"})))))

            updated-user (cond-> base-user
                           (contains? profile-data :name) (assoc :name (:name profile-data))
                           (contains? profile-data :username) (assoc :username (:username profile-data))
                           (contains? profile-data :email) (assoc :email (let [e (:email profile-data)]
                                                                           (if (str/blank? e) nil e)))
                           (contains? profile-data :password) (assoc :password (:password profile-data))
                           (contains? profile-data :position) (assoc :position (:position profile-data))
                           (contains? profile-data :phone) (assoc :phone (:phone profile-data))
                           (contains? profile-data :receive-non-mensalista-updates) (assoc :receive-non-mensalista-updates (:receive-non-mensalista-updates profile-data))
                           (contains? profile-data :avatar-filename) (assoc :avatar-filename (:avatar-filename profile-data)))

            final-user (if (:password profile-data)
                         (logic.user/encrypt-password updated-user)
                         updated-user)]
        (db.user/update-user-profile user-id final-user db)
        (enrich-user final-user user-id db)))))


