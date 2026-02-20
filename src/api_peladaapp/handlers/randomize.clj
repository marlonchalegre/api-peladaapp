(ns api-peladaapp.handlers.randomize
  (:require
   [api-peladaapp.controllers.pelada :as controller.pelada]
   [api-peladaapp.helpers.responses :as helper.response]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.randomize :as logic.randomize]
   [ring.util.response :as response]
   [schema.core :as s]))

(s/defschema RandomizeTeamsBody
  {:player_ids [s/Int]
   :players_per_team s/Int})

(defn randomize-teams
  [request]
  (if-let [pelada-id-str (-> request :params :id)]
    (let [pelada-id (Integer/parseInt pelada-id-str)
          {:keys [player_ids players_per_team]} (:body request)
          user-id (auth/get-user-id-from-request request)
          db (:database request)
          pelada (controller.pelada/get-pelada pelada-id db)
          org-id (:organization-id pelada)]
      (auth/require-organization-admin! user-id org-id db)
      (logic.randomize/randomize-teams! pelada-id player_ids players_per_team db)
      (response/response {:success true}))
    (helper.response/bad-request {:error "Missing pelada id"})))