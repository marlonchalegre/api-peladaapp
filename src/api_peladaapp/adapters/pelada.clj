(ns api-peladaapp.adapters.pelada
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.pelada :as models.pelada]
   [api-peladaapp.requests.pelada :as requests.pelada]
   [api-peladaapp.responses.pelada :as responses.pelada]
   [medley.core :as medley.core]
   [schema.core :as s]))

(s/defn create-request->model :- models.pelada/Pelada
  [request :- requests.pelada/CreatePeladaRequest]
  (let [scheduled-at (or (:scheduled_at request) (:when request))]
    (medley.core/assoc-some {}
                            :organization_id (:organization_id request)
                            :scheduled_at scheduled-at
                            :num_teams (:num_teams request)
                            :players_per_team (:players_per_team request)
                            :status (:status request))))

(s/defn update-request->model :- models.pelada/Pelada
  [request :- requests.pelada/UpdatePeladaRequest]
  (let [scheduled-at (or (:scheduled_at request) (:when request))]
    (medley.core/assoc-some {}
                            :organization_id (:organization_id request)
                            :scheduled_at scheduled-at
                            :num_teams (:num_teams request)
                            :players_per_team (:players_per_team request)
                            :status (:status request))))

(s/defn model->response :- responses.pelada/PeladaResponse
  [model :- models.pelada/Pelada]
  (select-keys model [:id :organization_id :scheduled_at :num_teams :players_per_team :status :closed_at]))

(s/defn db->model :- models.pelada/Pelada
  [pelada]
  (some-> pelada misc/unamespace (select-keys [:id :organization_id :scheduled_at :num_teams :players_per_team :status :closed_at])))