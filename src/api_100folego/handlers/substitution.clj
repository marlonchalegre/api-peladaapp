(ns api-100folego.handlers.substitution
  (:require [api-100folego.controllers.substitution :as controller.substitution]
            [api-100folego.helpers.exception :as exception]
            [api-100folego.helpers.responses :refer [created ok]]))

(defn create [request]
  (try (let [db (:database request)
             match-id (Integer/parseInt (str (get-in request [:params :id])))
             {:keys [out_player_id in_player_id minute]} (:body request)
             sub {:match_id match-id :out_player_id (Integer/parseInt (str out_player_id)) :in_player_id (Integer/parseInt (str in_player_id)) :minute minute}]
         (created (controller.substitution/create-substitution sub db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-match [request]
  (try (let [db (:database request)
             match-id (Integer/parseInt (str (get-in request [:params :id])))]
         (ok (controller.substitution/list-substitutions match-id db)))
       (catch Exception e (exception/api-exception-handler e))))
