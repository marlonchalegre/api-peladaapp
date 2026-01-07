(ns api-peladaapp.handlers.organization
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :refer [created deleted ok updated]]
   [api-peladaapp.logic.authorization :as auth]))

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
             id (get-in request [:params :id])]
         (-> (controller.organization/get-organization id db)
             adapter.organization/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             body (:body request)
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id id db)
         (-> (controller.organization/update-organization id (adapter.organization/update-request->model body) db)
             adapter.organization/model->response
             updated))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-admin! user-id id db)
         (deleted (controller.organization/delete-organization id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-all [request]
  (try (let [db (:database request)
             query-params (:query-params request)
             pagination (pagination/parse-pagination-params query-params)
             orgs-data (controller.organization/list-organizations db pagination)
             orgs-models (:data orgs-data)
             orgs-responses (map adapter.organization/model->response orgs-models)]
         (ok orgs-responses (:headers orgs-data)))
       (catch Exception e (exception/api-exception-handler e))))