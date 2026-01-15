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
                            :organization-id (:organization_id request)
                            :scheduled-at scheduled-at
                            :num-teams (:num_teams request)
                            :players-per-team (:players_per_team request)
                            :status (:status request))))

(s/defn update-request->model :- models.pelada/Pelada
  [request :- requests.pelada/UpdatePeladaRequest]
  (let [scheduled-at (or (:scheduled_at request) (:when request))]
    (medley.core/assoc-some {}
                            :organization-id (:organization_id request)
                            :scheduled-at scheduled-at
                            :num-teams (:num_teams request)
                            :players-per-team (:players_per_team request)
                            :status (:status request))))

(s/defn model->response :- responses.pelada/PeladaResponse
  [model :- models.pelada/Pelada]
  (let [{:keys [id organization-id scheduled-at num-teams players-per-team status closed-at]} model]
    (medley.core/assoc-some {}
                            :id id
                            :organization_id organization-id
                            :scheduled_at scheduled-at
                            :num_teams num-teams
                            :players_per_team players-per-team
                            :status status
                            :closed_at closed-at)))

(s/defn db->model :- models.pelada/Pelada
  [pelada]
  (when-let [p (some-> pelada misc/unamespace)]
    (medley.core/assoc-some {}
                            :id (:id p)
                            :organization-id (:organization_id p)
                            :scheduled-at (:scheduled_at p)
                            :num-teams (:num_teams p)
                            :players-per-team (:players_per_team p)
                            :status (:status p)
                            :closed-at (:closed_at p))))