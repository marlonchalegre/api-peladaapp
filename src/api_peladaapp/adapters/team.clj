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
                          :pelada_id (:pelada_id request)
                          :name (:name request)))

(s/defn update-request->model :- models.team/Team
  [request :- requests.team/UpdateTeamRequest]
  (medley.core/assoc-some {}
                          :pelada_id (:pelada_id request)
                          :name (:name request)))

(s/defn model->response :- responses.team/TeamResponse
  [model :- models.team/Team]
  (select-keys model [:id :pelada_id :name]))

(s/defn db->model :- models.team/Team
  [team]
  (some-> team misc/unamespace (select-keys [:id :pelada_id :name])))