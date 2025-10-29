(ns api-peladaapp.handlers.team
  (:require [api-peladaapp.adapters.team :as adapter.team]
            [api-peladaapp.controllers.team :as controller.team]
            [api-peladaapp.db.team :as db.team]
            [api-peladaapp.helpers.exception :as exception]
            [api-peladaapp.helpers.responses :refer [created ok updated deleted]]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             team (adapter.team/in->model body)
             id (controller.team/create-team team db)]
         (created {:id id}))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (ok (controller.team/get-team id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])
             body (:body request)]
         (updated (controller.team/update-team id body db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (deleted (controller.team/delete-team id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (get-in request [:params :pelada_id])]
         (ok (controller.team/list-teams pelada-id db)))
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
         (controller.team/add-player team-id player-id db)
         (created {:team_id team-id :player_id player-id}))
       (catch Exception e (exception/api-exception-handler e))))

(defn remove-player [request]
  (try (let [db (:database request)
             team-id (Integer/parseInt (str (get-in request [:params :id])))
             player-id (Integer/parseInt (str (get-in request [:body :player_id])))]
         (deleted (controller.team/remove-player team-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))
