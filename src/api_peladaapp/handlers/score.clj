(ns api-peladaapp.handlers.score
  (:require [api-peladaapp.logic.score :as logic.score]
            [ring.util.response :as response]
            [schema.core :as s]))

(s/defschema BulkScoreRequest
  {:player_ids [s/Int]})

(defn get-normalized-scores [request]
  (let [{:keys [player_ids]} (:body request)
        db (:database request)]
    (response/response
     {:scores (logic.score/get-normalized-scores player_ids db)})))