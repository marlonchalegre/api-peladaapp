(ns api-peladaapp.handlers.player
  (:require
   [api-peladaapp.adapters.player :as adapter.player]
   [api-peladaapp.controllers.player :as controller.player]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [created deleted ok updated]]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             player (adapter.player/create-request->model body)]
         (-> (controller.player/create-player player db)
             adapter.player/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (-> (controller.player/get-player id db)
             adapter.player/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             body (:body request)]
         (-> (controller.player/update-player id (adapter.player/update-request->model body) db)
             adapter.player/model->response
             updated))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (deleted (controller.player/delete-player id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-org [request]
  (try (let [db (:database request)
             org-id (get-in request [:params :organization_id])]
         (let [players (controller.player/list-players org-id db)]
           (ok (map adapter.player/model->response players))))
       (catch Exception e (exception/api-exception-handler e))))