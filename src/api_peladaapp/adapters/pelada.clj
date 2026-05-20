(ns api-peladaapp.adapters.pelada
  (:require
   [api-peladaapp.adapters.finance :as adapter.finance]
   [api-peladaapp.adapters.team :as adapter.team]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.time :as helpers.time]
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
                                    :organization-id (misc/as-uuid (:organization_id request))
                                    :scheduled-at scheduled-at
                                    :num-teams (:num_teams request)
                                    :players-per-team (:players_per_team request)
                                    :fixed-goalkeepers (:fixed_goalkeepers request)
                                    :status (:status request))
      (contains? request :home_fixed_goalkeeper_id) (assoc :home-fixed-goalkeeper-id (misc/as-uuid (:home_fixed_goalkeeper_id request)))
      (contains? request :away_fixed_goalkeeper_id) (assoc :away-fixed-goalkeeper-id (misc/as-uuid (:away_fixed_goalkeeper_id request))))))

(s/defn update-request->model :- models.pelada/Pelada
  [request :- requests.pelada/UpdatePeladaRequest]
  (let [scheduled-at (or (:scheduled_at request) (:when request))]
    (cond-> (medley.core/assoc-some {}
                                    :organization-id (misc/as-uuid (:organization_id request))
                                    :scheduled-at scheduled-at
                                    :num-teams (:num_teams request)
                                    :players-per-team (:players_per_team request)
                                    :fixed-goalkeepers (:fixed_goalkeepers request)
                                    :status (:status request)
                                    :timer-started-at (:timer_started_at request)
                                    :timer-accumulated-ms (:timer_accumulated_ms request)
                                    :timer-status (:timer_status request))
      (contains? request :home_fixed_goalkeeper_id) (assoc :home-fixed-goalkeeper-id (misc/as-uuid (:home_fixed_goalkeeper_id request)))
      (contains? request :away_fixed_goalkeeper_id) (assoc :away-fixed-goalkeeper-id (misc/as-uuid (:away_fixed_goalkeeper_id request))))))

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
         :timer_status (:timer-status model)))))

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
                                                 (if (boolean? (:fixed_goalkeepers p)) (:fixed_goalkeepers p) (= 1 (:fixed_goalkeepers p)))
                                                 false)
                            :home-fixed-goalkeeper-id (:home_fixed_goalkeeper_id p)
                            :away-fixed-goalkeeper-id (:away_fixed_goalkeeper_id p)
                            :status (:status p)
                            :closed-at (:closed_at p)
                            :timer-started-at (:timer_started_at p)
                            :timer-accumulated-ms (:timer_accumulated_ms p)
                            :timer-status (:timer_status p))))

(defn user->response [user]
  (when user
    (medley.core/assoc-some {}
                            :id (:id user)
                            :name (:name user)
                            :username (:username user)
                            :position (:position user)
                            :avatar_filename (:user-avatar-filename user (:avatar-filename user)))))

(defn player->response [player]
  (when player
    (let [updated-at (or (:attendance-updated-at player) (:updated-at player) (:updated_at player))]
      (medley.core/assoc-some {}
                              :id (:id player)
                              :organization_id (:organization-id player)
                              :user_id (:user-id player)
                              :grade (:grade player)
                              :position (:position player)
                              :member_type (:member-type player)
                              :user_name (:user-name player)
                              :user_username (:user-username player)
                              :user_position (:user-position player)
                              :user_avatar_filename (:user-avatar-filename player)
                              :user (user->response (:user player))
                              :is_goalkeeper (:is_goalkeeper player)
                              :attendance_status (:attendance-status player)
                              :attendance_updated_at (when updated-at (some-> updated-at helpers.time/->instant str))))))

(defn full-details->response [data]
  (let [pelada-resp (assoc (model->response (:pelada data))
                           :is_admin (:is-admin data)
                           :has_schedule_plan (:has-schedule-plan data))]
    {:pelada pelada-resp
     :teams (map (fn [team]
                   (assoc (adapter.team/model->response team)
                          :players (map player->response (:players team))))
                 (:teams data))
     :available_players (map player->response (:available-players data))
     :scores (:scores data)
     :attendance (map (fn [a]
                        (-> a
                            (misc/unamespace)
                            (assoc :player (player->response (:player a)))))
                      (:attendance data))
     :pelada_transactions (map adapter.finance/model->transaction-response (:pelada-transactions data))
     :voting_info (when-let [v (:voting-info data)]
                    {:is_voting_open (:voting-open? v)
                     :can_vote (:can-vote? v)
                     :has_voted (:has-voted? v)
                     :votes_cast (:votes-cast v)})
     :users_map (into {} (map (fn [[k v]] [k (user->response v)]) (:users-map data)))
     :org_players_map (into {} (map (fn [[k v]] [k (player->response v)]) (:org-players-map data)))}))
