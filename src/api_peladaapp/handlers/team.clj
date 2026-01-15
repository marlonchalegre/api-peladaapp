(ns api-peladaapp.handlers.team
  (:require
   [api-peladaapp.adapters.team :as adapter.team]
   [api-peladaapp.controllers.team :as controller.team]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [created deleted ok updated]]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             team (adapter.team/create-request->model body)]
         (-> (controller.team/create-team team db)
             adapter.team/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))]
         (-> (controller.team/get-team id db)
             adapter.team/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-by-id [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)]
         (-> (controller.team/update-team id (adapter.team/update-request->model body) db)
             adapter.team/model->response
             updated))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))]
         (deleted (controller.team/delete-team id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))]
         (ok (map adapter.team/model->response (controller.team/list-teams pelada-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-players [request]
  (try (let [db (:database request)
             team-id (Integer/parseInt (str (get-in request [:params :id])))]
         (ok (db.team/list-team-players team-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn add-player [request]
  (try (let [db (:database request)
             team-id (Integer/parseInt (str (get-in request [:params :id])))
             player-id (Integer/parseInt (str (get-in request [:body :player_id])))]
         (created (controller.team/add-player team-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn remove-player [request]
  (try (let [db (:database request)
             team-id (Integer/parseInt (str (get-in request [:params :id])))
             player-id (Integer/parseInt (str (get-in request [:body :player_id])))]
         (deleted (controller.team/remove-player team-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))