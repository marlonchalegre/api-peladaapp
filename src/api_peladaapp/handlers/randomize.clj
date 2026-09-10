(ns api-peladaapp.handlers.randomize
  (:require
   [api-peladaapp.controllers.pelada :as controller.pelada]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.responses :as helper.response]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.draw :as logic.draw]
   [ring.util.response :as response]
   [schema.core :as s]))

(s/defschema RandomizeTeamsBody
  {:player_ids [s/Uuid]
   :players_per_team s/Int
   (s/optional-key :algorithm) (apply s/enum logic.draw/algorithms)
   (s/optional-key :use_history) s/Bool})

(defn randomize-teams
  [request]
  (try
    (if-let [pelada-id-str (-> request :params :id)]
      (let [pelada-id (misc/as-uuid pelada-id-str)
            {:keys [player_ids players_per_team algorithm use_history]} (:body request)
            player-ids (map misc/as-uuid player_ids)
            user-id (auth/get-user-id-from-request request)
            db (:database request)
            pelada (controller.pelada/get-pelada pelada-id db)
            org-id (:organization-id pelada)]
        (auth/require-organization-admin! user-id org-id db)
        (let [result (logic.draw/draw-teams! pelada-id
                                             {:algorithm algorithm
                                              :use-history (true? use_history)
                                              :player-ids player-ids
                                              :players-per-team players_per_team}
                                             db)]
          (response/response (merge {:success true} result))))
      (helper.response/bad-request {:error "Missing pelada id"}))
    (catch Exception e (exception/api-exception-handler e))))
