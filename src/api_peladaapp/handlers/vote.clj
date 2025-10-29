(ns api-peladaapp.handlers.vote
  (:refer-clojure :exclude [cast])
  (:require [api-peladaapp.adapters.vote :as adapter.vote]
            [api-peladaapp.controllers.vote :as controller.vote]
            [api-peladaapp.helpers.exception :as exception]
            [api-peladaapp.helpers.responses :refer [created ok]]))

(defn cast [request]
  (try (let [db (:database request)
             body (:body request)
             vote (adapter.vote/in->model body)]
         (created (controller.vote/cast-vote vote db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn batch-cast [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))
             body (:body request)
             voter-id (:voter_id body)
             votes (:votes body)]
         (ok (controller.vote/batch-cast-votes pelada-id voter-id votes db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn voting-info [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))
             voter-id (Integer/parseInt (str (get-in request [:params :voter_id])))]
         (ok (controller.vote/get-voting-info pelada-id voter-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))]
         (ok (controller.vote/list-votes pelada-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn normalize-score [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))
             player-id (Integer/parseInt (str (get-in request [:params :player_id])))]
         (ok (controller.vote/compute-normalized-score pelada-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))
