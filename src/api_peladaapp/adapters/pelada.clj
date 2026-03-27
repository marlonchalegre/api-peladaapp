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
                                    :status (:status request)
                                    :timer-started-at (:timer_started_at request)
                                    :timer-accumulated-ms (:timer_accumulated_ms request)
                                    :timer-status (:timer_status request))
      (contains? request :home_fixed_goalkeeper_id) (assoc :home-fixed-goalkeeper-id (:home_fixed_goalkeeper_id request))
      (contains? request :away_fixed_goalkeeper_id) (assoc :away-fixed-goalkeeper-id (:away_fixed_goalkeeper_id request)))))

(s/defn model->response :- responses.pelada/PeladaResponse
  [model :- models.pelada/Pelada]
  (let [display-status (if (logic.vote/voting-open? model)
                         "voting"
                         (:status model))]
    (-> {:id (:id model)
         :organization_id (:organization-id model)
         :scheduled_at (:scheduled-at model)
         :num_teams (:num-teams model)
         :players_per_team (:players-per-team model)
         :fixed_goalkeepers (boolean (:fixed-goalkeepers model))
         :status display-status
         :closed_at (:closed-at model)}
        (medley.core/assoc-some
         :organization_name (:organization-name model)
         :home_fixed_goalkeeper_id (:home-fixed-goalkeeper-id model)
         :away_fixed_goalkeeper_id (:away-fixed-goalkeeper-id model)
         :timer_started_at (:timer-started-at model)
         :timer_accumulated_ms (:timer-accumulated-ms model)
         :timer_status (:timer-status model)
         :vote_ended_message_sent (boolean (:vote-ended-message-sent model))
         :vote_reminder_12h_sent (boolean (:vote-reminder-12h-sent model))
         :vote_reminder_23h_sent (boolean (:vote-reminder-23h-sent model))))))

(s/defn db->model :- models.pelada/Pelada
  [pelada]
  (when-let [p (some-> pelada misc/unamespace)]
    (medley.core/assoc-some {}
                            :id (:id p)
                            :organization-id (:organization_id p)
                            :organization-name (:organization_name p)
                            :scheduled-at (:scheduled_at p)
                            :num-teams (:num_teams p)
                            :players-per-team (:players_per_team p)
                            :fixed-goalkeepers (if (contains? p :fixed_goalkeepers)
                                                 (= 1 (:fixed_goalkeepers p))
                                                 false)
                            :home-fixed-goalkeeper-id (:home_fixed_goalkeeper_id p)
                            :away-fixed-goalkeeper-id (:away_fixed_goalkeeper_id p)
                            :status (:status p)
                            :closed-at (:closed_at p)
                            :timer-started-at (:timer_started_at p)
                            :timer-accumulated-ms (:timer_accumulated_ms p)
                            :timer-status (:timer_status p)
                            :vote-ended-message-sent (if (contains? p :vote_ended_message_sent)
                                                       (= 1 (:vote_ended_message_sent p))
                                                       false)
                            :vote-reminder-12h-sent (if (contains? p :vote_reminder_12h_sent)
                                                      (= 1 (:vote_reminder_12h_sent p))
                                                      false)
                            :vote-reminder-23h-sent (if (contains? p :vote_reminder_23h_sent)
                                                      (= 1 (:vote_reminder_23h_sent p))
                                                      false)
                            :last-attendance-reminder-at (:last_attendance_reminder_at p))))
