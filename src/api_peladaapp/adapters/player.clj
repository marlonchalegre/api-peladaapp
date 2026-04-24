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
                          :position-id (:position_id request)
                          :member-type (:member_type request)))

(s/defn update-request->model :- models.player/Player
  [request :- requests.player/UpdatePlayerRequest]
  (medley.core/assoc-some {}
                          :user-id (:user_id request)
                          :organization-id (:organization_id request)
                          :grade (:grade request)
                          :position-id (:position_id request)
                          :member-type (:member_type request)))

(s/defn model->response :- responses.player/PlayerResponse
  [{:keys [id user-id organization-id grade position-id member-type user-name user-username user-email user-position user-avatar-filename attendance-status attendance-updated-at]}]
  (let [m (medley.core/assoc-some {}
                                  :id id
                                  :user_id user-id
                                  :organization_id organization-id
                                  :grade grade
                                  :position_id position-id
                                  :member_type member-type
                                  :user_name user-name
                                  :user_username user-username
                                  :user_email user-email
                                  :user_position user-position
                                  :attendance_status attendance-status
                                  :attendance_updated_at attendance-updated-at)]
    (assoc m :user_avatar_filename user-avatar-filename)))

(s/defn db->model :- models.player/Player
  [p]
  (when-let [row (some-> p misc/unamespace)]
    (medley.core/assoc-some {}
                            :id (:id row)
                            :user-id (:user_id row)
                            :organization-id (:organization_id row)
                            :grade (:grade row)
                            :position-id (:position_id row)
                            :member-type (:member_type row)
                            :user-name (or (:user_name row) (:user-name row))
                            :user-username (or (:user_username row) (:user-username row))
                            :user-email (or (:user_email row) (:user-email row))
                            :user-position (or (:user_position row) (:user-position row))
                            :user-avatar-filename (:avatar_filename row))))
