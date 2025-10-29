(ns api-peladaapp.handlers.pelada
  (:require [api-peladaapp.adapters.pelada :as adapter.pelada]
            [api-peladaapp.controllers.pelada :as controller.pelada]
            [api-peladaapp.helpers.exception :as exception]
            [api-peladaapp.helpers.responses :refer [created ok updated deleted]]
            [api-peladaapp.logic.authorization :as auth]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             pelada (adapter.pelada/in->model body)
             user-id (auth/get-user-id-from-request request)
             org-id (:organization_id pelada)]
         ;; Only admins can create peladas
         (auth/require-organization-admin! user-id org-id db)
         (let [id (controller.pelada/create-pelada pelada db)]
           (created {:id id})))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization_id pelada)]
         ;; Members can view peladas
         (auth/require-organization-member! user-id org-id db)
         (ok pelada))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             body (:body request)
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization_id pelada)]
         ;; Only admins can update peladas
         (auth/require-organization-admin! user-id org-id db)
         (updated (controller.pelada/update-pelada id body db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization_id pelada)]
         ;; Only admins can delete peladas
         (auth/require-organization-admin! user-id org-id db)
         (deleted (controller.pelada/delete-pelada id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-org [request]
  (try (let [db (:database request)
             org-id (get-in request [:params :organization_id])
             user-id (auth/get-user-id-from-request request)]
         ;; Members can list peladas
         (auth/require-organization-member! user-id org-id db)
         (ok (controller.pelada/list-peladas org-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn begin [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)
             matches-per-team (some-> (:matches_per_team body) int)
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization_id pelada)]
         ;; Only admins can begin peladas
         (auth/require-organization-admin! user-id org-id db)
         (ok (if matches-per-team
               (controller.pelada/begin-pelada id db {:matches_per_team matches-per-team})
               (controller.pelada/begin-pelada id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn close [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization_id pelada)]
         ;; Only admins can close peladas
         (auth/require-organization-admin! user-id org-id db)
         (ok (controller.pelada/close-pelada id db)))
       (catch Exception e (exception/api-exception-handler e))))
