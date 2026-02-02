(ns api-peladaapp.handlers.player
  (:require
   [api-peladaapp.adapters.player :as adapter.player]
   [api-peladaapp.controllers.player :as controller.player]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [created deleted ok]]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             player (adapter.player/create-request->model body)]
         (-> (controller.player/create-player player db)
             adapter.player/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (deleted (controller.player/delete-player id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-org [request]
  (try (let [db (:database request)
             org-id (get-in request [:params :organization_id])]
         (ok (map adapter.player/model->response (controller.player/list-players org-id db))))
       (catch Exception e (exception/api-exception-handler e))))
