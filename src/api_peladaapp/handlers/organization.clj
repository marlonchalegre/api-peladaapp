(ns api-peladaapp.handlers.organization
  (:refer-clojure :exclude [update])
  (:require
   [api-peladaapp.adapters.invitation :as adapter.invitation]
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.controllers.user :as controller.user]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.responses :refer [bad-request created deleted ok]]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.monthly-substitution :as logic.monthly-sub]
   [clojure.string :as str]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             org (adapter.organization/create-request->model body)
             user-id (auth/get-user-id-from-request request)
             user (controller.user/get-user user-id db)]
         (if (false? (:allow-org-creation user))
           (throw (ex-info "Você não tem permissão para criar organizações"
                           {:type :forbidden :message "Você não tem permissão para criar organizações"}))
           (-> (controller.organization/create-organization org user-id db)
               adapter.organization/model->response
               created)))
       (catch Exception e (exception/api-exception-handler e))))

(defn leave [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id org-id db)
         (controller.organization/leave-organization org-id user-id db)
         (deleted))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id id db)
         (-> (controller.organization/get-organization id db)
             adapter.organization/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn update [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             org (adapter.organization/update-request->model body)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id id db)
         (-> (controller.organization/update-organization id org db)
             adapter.organization/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id id db)
         (deleted (controller.organization/delete-organization id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-user [request]
  (try (let [db (:database request)
             target-user-id (misc/as-uuid (get-in request [:params :user_id]))]
         (auth/require-self-or-admin! request target-user-id)
         (ok (map adapter.organization/model->response (controller.organization/list-user-organizations target-user-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-statistics [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             year (or (some-> (get-in request [:query-params "year"]) str Integer/parseInt) 0)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id id db)
         (auth/require-feature-flag! id :org_statistics db)
         (-> (controller.organization/get-statistics id year db)
             ok))
       (catch NumberFormatException _ (bad-request "Invalid ID or Year format"))
       (catch Exception e (exception/api-exception-handler e))))

(defn invite [request]
  (try (let [db (:database request)
             organization-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             email (get-in request [:body :email])
             name (get-in request [:body :name])]
         (auth/require-organization-admin! user-id organization-id db)
         (auth/check-member-limit! organization-id db)
         (if (and (str/blank? email) (str/blank? name))
           (bad-request "Email or Name is required")
           (-> (controller.organization/invite-player-improved organization-id email name user-id db)
               adapter.organization/invite-player-response->frontend
               ok)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-invite-link [request]
  (try (let [db (:database request)
             organization-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id organization-id db)
         (ok {:token (controller.organization/get-or-create-organization-link organization-id user-id db)}))
       (catch Exception e (exception/api-exception-handler e))))

(defn reset-invite-link [request]
  (try (let [db (:database request)
             organization-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id organization-id db)
         (ok {:token (controller.organization/reset-organization-link organization-id user-id db)}))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-invitation-info [request]
  (try (let [db (:database request)
             token (get-in request [:params :token])]
         (ok (adapter.invitation/model->response (controller.organization/get-invitation-by-token token db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-pending-invitations [request]
  (try (let [db (:database request)
             user-id (auth/get-user-id-from-request request)
             user (controller.user/get-user user-id db)]
         (ok (map adapter.invitation/model->response
                  (controller.organization/list-pending-invitations-for-user (:email user) (:username user) db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn accept-invitation [request]
  (try (let [db (:database request)
             user-id (auth/get-user-id-from-request request)
             token (get-in request [:params :token])]
         (-> (controller.organization/accept-invitation token user-id db)
             adapter.organization/accept-invitation-response->frontend
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-invitations [request]
  (try (let [db (:database request)
             organization-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id organization-id db)
         (ok (map adapter.invitation/model->response (controller.organization/list-organization-invitations organization-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn revoke-invitation [request]
  (try (let [db (:database request)
             organization-id (misc/as-uuid (get-in request [:params :id]))
             invitation-id (misc/as-uuid (get-in request [:params :invitation_id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id organization-id db)
         (controller.organization/revoke-invitation invitation-id organization-id db)
         (deleted))
       (catch Exception e (exception/api-exception-handler e))))

(defn test-waha [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id id db)
         (auth/require-feature-flag! id :waha_communications db)
         (ok (controller.organization/test-waha-connection id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-substitutions [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id org-id db)
         (auth/require-feature-flag! org-id :monthly_substitutions db)
         (let [subs (controller.organization/list-monthly-substitutions org-id db)]
           (ok (map (comp adapter.organization/model->substitution-response
                          adapter.organization/db->substitution)
                    subs))))
       (catch Exception e (exception/api-exception-handler e))))

(defn create-substitution [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)
             body (:body request)
             permanent-player-id (misc/as-uuid (:permanent_player_id body))
             temporary-player-id (misc/as-uuid (:temporary_player_id body))
             start-date (:start_date body)]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :monthly_substitutions db)
         (ok (logic.monthly-sub/substitute-player! org-id permanent-player-id temporary-player-id start-date db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn end-substitution [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)
             sub-id (misc/as-uuid (get-in request [:params :sub_id]))
             end-date (get-in request [:body :end_date] (str (java.time.LocalDate/now)))]
         (auth/require-organization-admin! user-id org-id db)
         (auth/require-feature-flag! org-id :monthly_substitutions db)
         (ok (logic.monthly-sub/end-substitution! sub-id end-date db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-organization-feature-flags [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id id db)
         (let [flags (db.organization/get-organization-feature-flags id db)]
           (if flags
             (ok (dissoc flags :created_at :updated_at))
             (do
               (db.organization/insert-default-feature-flags id db)
               (ok (dissoc (db.organization/get-organization-feature-flags id db) :created_at :updated_at))))))
       (catch Exception e (exception/api-exception-handler e))))



