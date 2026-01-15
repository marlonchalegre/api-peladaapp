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
                          :user-id (:user_id request)
                          :organization-id (:organization_id request)
                          :grade (:grade request)
                          :position-id (:position_id request)))

(s/defn update-request->model :- models.player/Player
  [request :- requests.player/UpdatePlayerRequest]
  (medley.core/assoc-some {}
                          :user-id (:user_id request)
                          :organization-id (:organization_id request)
                          :grade (:grade request)
                          :position-id (:position_id request)))

(s/defn model->response :- responses.player/PlayerResponse
  [{:keys [id user-id organization-id grade position-id]}]
  (medley.core/assoc-some {}
                          :id id
                          :user_id user-id
                          :organization_id organization-id
                          :grade grade
                          :position_id position-id))

(s/defn db->model :- models.player/Player
  [p]
  (when-let [row (some-> p misc/unamespace)]
    (medley.core/assoc-some {}
                            :id (:id row)
                            :user-id (:user_id row)
                            :organization-id (:organization_id row)
                            :grade (:grade row)
                            :position-id (:position_id row))))