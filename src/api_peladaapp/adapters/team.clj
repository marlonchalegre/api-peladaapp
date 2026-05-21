(ns api-peladaapp.adapters.team
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.team :as models.team]
   [api-peladaapp.requests.team :as requests.team]
   [api-peladaapp.responses.team :as responses.team]
   [medley.core :as medley.core]
   [schema.core :as s]))

(s/defn create-request->model :- models.team/Team
  [request :- requests.team/CreateTeamRequest]
  (medley.core/assoc-some {}
                          :pelada-id (misc/as-uuid (:pelada_id request))
                          :name (:name request)))

(s/defn update-request->model :- models.team/Team
  [request :- requests.team/UpdateTeamRequest]
  (medley.core/assoc-some {}
                          :pelada-id (misc/as-uuid (:pelada_id request))
                          :name (:name request)))

(s/defn model->response :- responses.team/TeamResponse
  [{:keys [id pelada-id name]}]
  (medley.core/assoc-some {}
                          :id id
                          :pelada_id pelada-id
                          :name name))

(s/defn db->model :- models.team/Team
  [team]
  (when-let [row (some-> team misc/unamespace)]
    (medley.core/assoc-some {}
                            :id (:id row)
                            :pelada-id (:pelada_id row)
                            :name (:name row))))

(s/defn team-player->response :- responses.team/TeamPlayerResponse
  [model :- models.team/TeamPlayer]
  {:team_id (:team-id model)
   :player_id (:player-id model)
   :is_goalkeeper (boolean (:is-goalkeeper model))})