(ns api-100folego.handlers.team
  (:require [api-100folego.adapters.team :as adapter.team]
            [api-100folego.controllers.team :as controller.team]
            [api-100folego.db.team :as db.team]
            [api-100folego.helpers.exception :as exception]
            [api-100folego.helpers.responses :refer [created ok updated deleted]]))

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
         (do (controller.team/add-player team-id player-id db)
             (created {:team_id team-id :player_id player-id})))
       (catch Exception e (exception/api-exception-handler e))))

(defn remove-player [request]
  (try (let [db (:database request)
             team-id (Integer/parseInt (str (get-in request [:params :id])))
             player-id (Integer/parseInt (str (get-in request [:body :player_id])))]
         (deleted (controller.team/remove-player team-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))
