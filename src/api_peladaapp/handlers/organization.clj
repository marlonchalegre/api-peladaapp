(ns api-peladaapp.handlers.organization
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [bad-request created deleted ok]]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.string :as str]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             org (adapter.organization/create-request->model body)
             user-id (auth/get-user-id-from-request request)]
         (-> (controller.organization/create-organization org user-id db)
             adapter.organization/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))]
         (-> (controller.organization/get-organization id db)
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
             user-id (Integer/parseInt (str (get-in request [:params :user_id])))]
         (ok (controller.organization/list-user-organizations user-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-statistics [request]
  (try (let [db (:database request)
             id (Integer/parseInt (get-in request [:params :id]))
             year (Integer/parseInt (get-in request [:query-params "year"]))]
         (-> (controller.organization/get-statistics id year db)
             ok))
       (catch NumberFormatException _ (bad-request "Invalid ID or Year format"))
       (catch Exception e (exception/api-exception-handler e))))

(defn invite [request]
  (try (let [db (:database request)
             organization-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             email (get-in request [:body :email])]
         (auth/require-organization-admin! user-id organization-id db)
         (if (str/blank? email)
           (bad-request "Email is required")
           (-> (controller.organization/invite-player organization-id email user-id db)
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
         (ok (controller.organization/get-invitation-by-token token db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-pending-invitations [request]
  (try (let [db (:database request)
             user-id (auth/get-user-id-from-request request)
             user (db.user/find-user-by-id user-id db)]
         (ok (controller.organization/list-pending-invitations (:email user) db)))
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
         (ok (controller.organization/list-organization-invitations organization-id db)))
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


