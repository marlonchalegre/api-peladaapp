(ns api-peladaapp.controllers.user
  (:require
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.grade :as logic.grade]
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

(defn- start-of-week
  "Monday (as a LocalDate) of the week a timestamp belongs to, in UTC."
  [ts]
  (let [inst (helpers.time/->instant ts)
        local-date (.toLocalDate (.atZone inst (java.time.ZoneId/of "UTC")))]
    (.with local-date
           (java.time.temporal.TemporalAdjusters/previousOrSame java.time.DayOfWeek/MONDAY))))

(defn- build-presence
  "Last 12 weeks of activity for the user, oldest first. Each week is
   `present` when they had at least one confirmed pelada, `absent` when they
   declined one and confirmed none, and `no_game` otherwise."
  [peladas-with-attendance]
  (let [by-week (group-by (fn [p] (some-> (:scheduled_at p) start-of-week str))
                          (remove #(nil? (:scheduled_at %)) peladas-with-attendance))
        statuses (fn [rows]
                   (let [states (set (map #(some-> (:attendance_status %) name) rows))]
                     (cond
                       (contains? states "confirmed") "present"
                       (contains? states "declined") "absent"
                       :else "no_game")))]
    (->> by-week
         (sort-by key)
         (take-last 12)
         (mapv (fn [[week rows]] {:week_start week :status (statuses rows)})))))

(s/defn get-profile-dashboard
  "Aggregated profile for a user: season summary, skills, per-group stats,
   12-week presence and their recent peladas with own scouts and awards."
  [user-id :- s/Uuid
   year :- s/Int
   db]
  (let [orgs (db.organization/list-by-user user-id db)
        per-org (mapv (fn [org]
                        {:organization_id (:id org)
                         :organization_name (:name org)
                         :peladas (controller.organization/get-history (:id org) user-id year db)})
                      orgs)
        entries (vec (mapcat (fn [{:keys [organization_id organization_name peladas]}]
                               (map #(assoc % :organization_id organization_id
                                            :organization_name organization_name)
                                    peladas))
                             per-org))
        played (filter :user entries)
        rating-samples (keep #(get-in % [:user :avg_stars]) played)
        avg-stars (if (seq rating-samples)
                    (/ (reduce + rating-samples) (count rating-samples))
                    0.0)
        attendance (db.user/list-user-peladas-with-attendance user-id year db)
        decided (filter #(contains? #{"confirmed" "declined"}
                                    (some-> (:attendance_status %) name))
                        attendance)
        present (count (filter #(= "confirmed" (some-> (:attendance_status %) name)) decided))
        summary {:avg_rating (when (seq rating-samples)
                               (logic.grade/performance-from-stars avg-stars))
                 :avg_stars (when (seq rating-samples) avg-stars)
                 :matches_played (count played)
                 :goals (reduce + 0 (map #(get-in % [:user :goals] 0) played))
                 :assists (reduce + 0 (map #(get-in % [:user :assists] 0) played))
                 :titles (count (filter #(= 1 (get-in % [:user :team_position])) played))
                 :mvp_count (count (filter #(get-in % [:user :is_mvp]) played))
                 :garcom_count (count (filter #(get-in % [:user :is_garcom]) played))
                 :attendance_rate (when (pos? (count decided))
                                    (* 100.0 (/ present (count decided))))}
        groups (mapv (fn [{:keys [organization_id organization_name peladas]}]
                       (let [p (filter :user peladas)]
                         {:organization_id organization_id
                          :organization_name organization_name
                          :peladas_played (count p)
                          :goals (reduce + 0 (map #(get-in % [:user :goals] 0) p))
                          :assists (reduce + 0 (map #(get-in % [:user :assists] 0) p))
                          :titles (count (filter #(= 1 (get-in % [:user :team_position])) p))}))
                     per-org)]
    {:year year
     :summary summary
     :skills (db.user/get-user-skills user-id db)
     :groups groups
     :presence (build-presence attendance)
     :recent_peladas (->> entries
                          (sort-by :scheduled_at (fn [a b] (compare b a)))
                          (take 20)
                          vec)}))


