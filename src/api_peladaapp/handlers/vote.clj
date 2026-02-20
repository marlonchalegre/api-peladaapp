(ns api-peladaapp.handlers.vote
  (:refer-clojure :exclude [cast])
  (:require
   [api-peladaapp.controllers.vote :as controller.vote]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :as responses :refer [ok]]
   [api-peladaapp.logic.authorization :as auth]))

(defn batch-cast [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))
             user-id (auth/get-user-id-from-request request)
             ;; We need the player-id, not the user-id, for the votes table
             voting-info (controller.vote/get-voting-info pelada-id user-id db)
             voter-player-id (:voter_player_id voting-info)
             body (:body request)
             votes (map (fn [v] {:target-id (:target_id v) :stars (:stars v)}) (:votes body))]

         (if-not voter-player-id
           (responses/forbidden {:message (or (:message voting-info) "User cannot vote in this pelada")})
           (ok (controller.vote/batch-cast-votes pelada-id voter-player-id votes db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn voting-info [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :pelada_id])))
             user-id (auth/get-user-id-from-request request)]
         ;; Voting info response is already in correct format
         (ok (controller.vote/get-voting-info pelada-id user-id db)))
       (catch Exception e (exception/api-exception-handler e))))
