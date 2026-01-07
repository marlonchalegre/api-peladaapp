(ns api-peladaapp.adapters.player
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.player :as models.player]
   [api-peladaapp.requests.player :as requests.player]
   [api-peladaapp.responses.player :as responses.player]
   [medley.core :as medley.core]
   [schema.core :as s]))

(s/defn create-request->model :- models.player/Player
  [request :- requests.player/CreatePlayerRequest]
  (medley.core/assoc-some {}
                          :user_id (:user_id request)
                          :organization_id (:organization_id request)
                          :grade (:grade request)
                          :position_id (:position_id request)))

(s/defn update-request->model :- models.player/Player
  [request :- requests.player/UpdatePlayerRequest]
  (medley.core/assoc-some {}
                          :user_id (:user_id request)
                          :organization_id (:organization_id request)
                          :grade (:grade request)
                          :position_id (:position_id request)))

(s/defn model->response :- responses.player/PlayerResponse
  [model :- models.player/Player]
  (select-keys model [:id :user_id :organization_id :grade :position_id]))

(s/defn db->model :- models.player/Player
  [p]
  (some-> p misc/unamespace (select-keys [:id :user_id :organization_id :grade :position_id])))