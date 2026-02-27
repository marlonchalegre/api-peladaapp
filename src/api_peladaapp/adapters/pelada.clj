(ns api-peladaapp.adapters.pelada
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.logic.vote :as logic.vote]
   [api-peladaapp.models.pelada :as models.pelada]
   [api-peladaapp.requests.pelada :as requests.pelada]
   [api-peladaapp.responses.pelada :as responses.pelada]
   [medley.core :as medley.core]
   [schema.core :as s]))

(s/defn create-request->model :- models.pelada/Pelada
  [request :- requests.pelada/CreatePeladaRequest]
  (let [scheduled-at (or (:scheduled_at request) (:when request))]
    (cond-> (medley.core/assoc-some {}
                                    :organization-id (:organization_id request)
                                    :scheduled-at scheduled-at
                                    :num-teams (:num_teams request)
                                    :players-per-team (:players_per_team request)
                                    :fixed-goalkeepers (:fixed_goalkeepers request)
                                    :status (:status request))
      (contains? request :home_fixed_goalkeeper_id) (assoc :home-fixed-goalkeeper-id (:home_fixed_goalkeeper_id request))
      (contains? request :away_fixed_goalkeeper_id) (assoc :away-fixed-goalkeeper-id (:away_fixed_goalkeeper_id request)))))

(s/defn update-request->model :- models.pelada/Pelada
  [request :- requests.pelada/UpdatePeladaRequest]
  (let [scheduled-at (or (:scheduled_at request) (:when request))]
    (cond-> (medley.core/assoc-some {}
                                    :organization-id (:organization_id request)
                                    :scheduled-at scheduled-at
                                    :num-teams (:num_teams request)
                                    :players-per-team (:players_per_team request)
                                    :fixed-goalkeepers (:fixed_goalkeepers request)
                                    :status (:status request))
      (contains? request :home_fixed_goalkeeper_id) (assoc :home-fixed-goalkeeper-id (:home_fixed_goalkeeper_id request))
      (contains? request :away_fixed_goalkeeper_id) (assoc :away-fixed-goalkeeper-id (:away_fixed_goalkeeper_id request)))))

(s/defn model->response :- responses.pelada/PeladaResponse
  [model :- models.pelada/Pelada]
  (let [display-status (if (logic.vote/voting-open? model)
                         "voting"
                         (:status model))]
    (medley.core/assoc-some {}
                            :id (:id model)
                            :organization_id (:organization-id model)
                            :organization_name (:organization-name model)
                            :scheduled_at (:scheduled-at model)
                            :num_teams (:num-teams model)
                            :players_per_team (:players-per-team model)
                            :fixed_goalkeepers (:fixed-goalkeepers model)
                            :home_fixed_goalkeeper_id (:home-fixed-goalkeeper-id model)
                            :away_fixed_goalkeeper_id (:away-fixed-goalkeeper-id model)
                            :status display-status
                            :closed_at (:closed-at model))))

(s/defn db->model :- models.pelada/Pelada
  [pelada]
  (when-let [p (some-> pelada misc/unamespace)]
    (medley.core/assoc-some {}
                            :id (:id p)
                            :organization-id (:organization_id p)
                            :organization_name (:organization_name p)
                            :scheduled_at (:scheduled-at p)
                            :num_teams (:num_teams p)
                            :players-per-team (:players_per_team p)
                            :fixed-goalkeepers (if (contains? p :fixed_goalkeepers)
                                                 (not= 0 (:fixed_goalkeepers p))
                                                 false)
                            :home-fixed-goalkeeper-id (:home_fixed_goalkeeper_id p)
                            :away-fixed-goalkeeper-id (:away_fixed_goalkeeper_id p)
                            :status (:status p)
                            :closed-at (:closed_at p))))
