(ns api-peladaapp.handlers.pelada
  (:require [api-peladaapp.adapters.pelada :as adapter.pelada]
            [api-peladaapp.controllers.pelada :as controller.pelada]
            [api-peladaapp.helpers.exception :as exception]
            [api-peladaapp.helpers.responses :refer [created ok updated deleted]]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             pelada (adapter.pelada/in->model body)
             id (controller.pelada/create-pelada pelada db)]
         (created {:id id}))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (ok (controller.pelada/get-pelada id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             body (:body request)]
         (updated (controller.pelada/update-pelada id body db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (deleted (controller.pelada/delete-pelada id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-org [request]
  (try (let [db (:database request)
             org-id (get-in request [:params :organization_id])]
         (ok (controller.pelada/list-peladas org-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn begin [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)
             matches-per-team (some-> (:matches_per_team body) int)]
         (ok (if matches-per-team
               (controller.pelada/begin-pelada id db {:matches_per_team matches-per-team})
               (controller.pelada/begin-pelada id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn close [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (ok (controller.pelada/close-pelada id db)))
       (catch Exception e (exception/api-exception-handler e))))
