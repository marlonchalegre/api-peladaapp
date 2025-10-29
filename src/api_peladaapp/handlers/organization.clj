(ns api-peladaapp.handlers.organization
  (:require [api-peladaapp.adapters.organization :as adapter.organization]
            [api-peladaapp.controllers.organization :as controller.organization]
            [api-peladaapp.helpers.exception :as exception]
            [api-peladaapp.helpers.responses :refer [created ok updated deleted]]
            [api-peladaapp.logic.authorization :as auth]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             org (adapter.organization/in->model body)
             user-id (auth/get-user-id-from-request request)]
         (created (controller.organization/create-organization org user-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (ok (controller.organization/get-organization id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             body (:body request)]
         (updated (controller.organization/update-organization id body db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (deleted (controller.organization/delete-organization id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-all [request]
  (try (let [db (:database request)]
         (ok (controller.organization/list-organizations db)))
       (catch Exception e (exception/api-exception-handler e))))
