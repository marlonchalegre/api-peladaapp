(ns api-peladaapp.handlers.organization
  (:refer-clojure :exclude [update])
  (:require
   [api-peladaapp.adapters.invitation :as adapter.invitation]
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [bad-request created deleted ok]]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.string :as str]
   [next.jdbc :as jdbc]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             org (adapter.organization/create-request->model body)
             user-id (auth/get-user-id-from-request request)]
         (-> (controller.organization/create-organization org user-id db)
             adapter.organization/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn leave [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id org-id db)
         (let [is-admin? (db.admin/is-user-admin-of-organization? user-id org-id db)
               admin-count (db.admin/count-admins-by-organization org-id db)
               player (db.player/get-org-player-by-user-id user-id org-id db)]
           (when (and is-admin? (<= admin-count 1))
             (throw (ex-info "Cannot leave organization: you are the last administrator."
                             {:type :bad-request
                              :message "Cannot leave organization: you are the last administrator."})))
           (jdbc/with-transaction [tx db]
             (when is-admin?
               (db.admin/delete-organization-admin-by-org-and-user org-id user-id tx))
             (when player
               (db.player/delete-player (:id player) tx)))
           (deleted)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id id db)
         (-> (controller.organization/get-organization id db)
             adapter.organization/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn update [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))
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
             id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id id db)
         (deleted (controller.organization/delete-organization id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-user [request]
  (try (let [db (:database request)
             target-user-id (Integer/parseInt (str (get-in request [:params :user_id])))]
         (auth/require-self-or-admin! request target-user-id)
         (ok (controller.organization/list-user-organizations target-user-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-statistics [request]
  (try (let [db (:database request)
             id (Integer/parseInt (get-in request [:params :id]))
             year (Integer/parseInt (get-in request [:query-params "year"]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id id db)
         (-> (controller.organization/get-statistics id year db)
             ok))
       (catch NumberFormatException _ (bad-request "Invalid ID or Year format"))
       (catch Exception e (exception/api-exception-handler e))))

(defn invite [request]
  (try (let [db (:database request)
             organization-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             email (get-in request [:body :email])
             name (get-in request [:body :name])]
         (auth/require-organization-admin! user-id organization-id db)
         (if (and (str/blank? email) (str/blank? name))
           (bad-request "Email or Name is required")
           (-> (controller.organization/invite-player-improved organization-id email name user-id db)
               adapter.organization/invite-player-response->frontend
               ok)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-invite-link [request]
  (try (let [db (:database request)
             organization-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id organization-id db)
         (ok {:token (controller.organization/get-or-create-organization-link organization-id user-id db)}))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-invitation-info [request]
  (try (let [db (:database request)
             token (get-in request [:params :token])]
         (ok (adapter.invitation/model->response (controller.organization/get-invitation-by-token token db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-pending-invitations [request]
  (try (let [db (:database request)
             user-id (auth/get-user-id-from-request request)
             user (db.user/find-user-by-id user-id db)]
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
             organization-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id organization-id db)
         (ok (map adapter.invitation/model->response (controller.organization/list-organization-invitations organization-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn revoke-invitation [request]
  (try (let [db (:database request)
             organization-id (Integer/parseInt (str (get-in request [:params :id])))
             invitation-id (Integer/parseInt (str (get-in request [:params :invitation_id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id organization-id db)
         (controller.organization/revoke-invitation invitation-id organization-id db)
         (deleted))
       (catch Exception e (exception/api-exception-handler e))))

(defn test-waha [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id id db)
         (ok (controller.organization/test-waha-connection id db)))
       (catch Exception e (exception/api-exception-handler e))))


