(ns api-100folego.handlers.organization
  (:require [api-100folego.adapters.organization :as adapter.organization]
            [api-100folego.controllers.organization :as controller.organization]
            [api-100folego.helpers.exception :as exception]
            [api-100folego.helpers.responses :refer [created ok updated deleted]]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             org (adapter.organization/in->model body)]
         (created (controller.organization/create-organization org db)))
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
