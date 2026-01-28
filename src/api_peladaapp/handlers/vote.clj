(ns api-peladaapp.handlers.vote
  (:refer-clojure :exclude [cast])
  (:require
   [api-peladaapp.adapters.vote :as adapter.vote]
   [api-peladaapp.controllers.vote :as controller.vote]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [created ok]]
   [api-peladaapp.logic.authorization :as auth]))

(defn cast [request]
  (try (let [db (:database request)
             body (:body request)
             vote (adapter.vote/create-request->model body)]
         (-> (controller.vote/cast-vote vote db)
             adapter.vote/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn batch-cast [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))
             body (:body request)
             voter-id (:voter_id body)
             votes (map (fn [v] {:target-id (:target_id v) :stars (:stars v)}) (:votes body))]
         ;; Batch cast response is already in correct format
         (ok (controller.vote/batch-cast-votes pelada-id voter-id votes db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn voting-info [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))
             user-id (auth/get-user-id-from-request request)]
         ;; Voting info response is already in correct format
         (ok (controller.vote/get-voting-info pelada-id user-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))]
         (ok (map adapter.vote/model->response (controller.vote/list-votes pelada-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn normalize-score [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))
             player-id (Integer/parseInt (str (get-in request [:params :player_id])))]
         ;; Normalized score response is already in correct format
         (ok (controller.vote/compute-normalized-score pelada-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))