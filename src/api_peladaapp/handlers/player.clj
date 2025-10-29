(ns api-peladaapp.handlers.player
  (:require [api-peladaapp.adapters.player :as adapter.player]
            [api-peladaapp.controllers.player :as controller.player]
            [api-peladaapp.helpers.exception :as exception]
            [api-peladaapp.helpers.responses :refer [created ok updated deleted]]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             player (adapter.player/in->model body)
             id (controller.player/create-player player db)]
         (created {:id id}))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (ok (controller.player/get-player id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             body (:body request)]
         (updated (controller.player/update-player id body db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (deleted (controller.player/delete-player id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-org [request]
  (try (let [db (:database request)
             org-id (get-in request [:params :organization_id])]
         (ok (controller.player/list-players org-id db)))
       (catch Exception e (exception/api-exception-handler e))))
