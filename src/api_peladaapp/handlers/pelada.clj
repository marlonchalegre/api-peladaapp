(ns api-peladaapp.handlers.pelada
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [api-peladaapp.controllers.pelada :as controller.pelada]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :refer [created deleted ok updated]]
   [api-peladaapp.logic.authorization :as auth]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             pelada (adapter.pelada/create-request->model body)
             user-id (auth/get-user-id-from-request request)
             org-id (:organization_id pelada)]
         ;; Only admins can create peladas
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.pelada/create-pelada pelada db)
             adapter.pelada/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization_id pelada)]
         ;; Members can view peladas
         (auth/require-organization-member! user-id org-id db)
         (-> pelada
             adapter.pelada/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-full-details [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             pelada-data (controller.pelada/get-pelada-full-details-controller id user-id db)
             org-id (get-in pelada-data [:pelada :organization_id])]
         ;; Members can view peladas
         (auth/require-organization-member! user-id org-id db)
         ;; Returning as is (map) since it's a complex response defined in controller return type
         (ok pelada-data))
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
         (-> (controller.pelada/update-pelada id (adapter.pelada/update-request->model body) db)
             adapter.pelada/model->response
             updated))
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
             user-id (auth/get-user-id-from-request request)
             query-params (:query-params request)
             pagination (pagination/parse-pagination-params query-params)]
         ;; Members can list peladas
         (auth/require-organization-member! user-id org-id db)
         (let [peladas-data (controller.pelada/list-peladas org-id db pagination)
               peladas-models (:data peladas-data)
               peladas-responses (map adapter.pelada/model->response peladas-models)]
           (ok peladas-responses (:headers peladas-data))))
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
         (-> (controller.pelada/close-pelada id db)
             adapter.pelada/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-dashboard-data [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization_id pelada)]
         ;; Members can view peladas
         (auth/require-organization-member! user-id org-id db)
         (ok (controller.pelada/get-pelada-dashboard-data id db)))
       (catch Exception e (exception/api-exception-handler e))))
