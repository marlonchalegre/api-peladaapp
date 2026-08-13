(ns api-peladaapp.adapters.pelada
  (:require
   [api-peladaapp.adapters.attendance :as adapter.attendance]
   [api-peladaapp.adapters.finance :as adapter.finance]
   [api-peladaapp.adapters.match :as adapter.match]
   [api-peladaapp.adapters.player :as adapter.player]
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
      (contains? request :away_fixed_goalkeeper_id) (assoc :away-fixed-goalkeeper-id (misc/as-uuid (:away_fixed_goalkeeper_id request)))
      (contains? request :notify_casual_players) (assoc :notify-casual-players (boolean (:notify_casual_players request))))))

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
      (contains? request :away_fixed_goalkeeper_id) (assoc :away-fixed-goalkeeper-id (misc/as-uuid (:away_fixed_goalkeeper_id request)))
      (contains? request :notify_casual_players) (assoc :notify-casual-players (boolean (:notify_casual_players request))))))

(s/defn model->response :- responses.pelada/PeladaResponse
  [model :- models.pelada/Pelada]
  (let [display-status (if (logic.vote/voting-open? model)
                         "voting"
                         (:status model))]
    (-> {:id (:id model)
         :organization_id (:organization-id model)
         :scheduled_at (some-> (:scheduled-at model) helpers.time/->instant str)
         :num_teams (:num-teams model)
         :players_per_team (:players-per-team model)
         :fixed_goalkeepers (boolean (:fixed-goalkeepers model))
         :status display-status
         :closed_at (:closed-at model)}
        (medley.core/assoc-some
         :organization_name (:organization-name model)
         :home_fixed_goalkeeper_id (:home-fixed-goalkeeper-id model)
         :away_fixed_goalkeeper_id (:away-fixed-goalkeeper-id model)
         :notify_casual_players (if (nil? (:notify-casual-players model)) true (boolean (:notify-casual-players model)))
         :timer_started_at (some-> (:timer-started-at model) helpers.time/->instant str)
         :timer_accumulated_ms (:timer-accumulated-ms model)
         :timer_status (:timer-status model)
         :user_attendance_status (:user-attendance-status model)))))

(defn begin-model->response [model]
  {:matches_created (:matches-created model)})

(defn- db-bool
  [val default-val]
  (if (nil? val)
    default-val
    (if (boolean? val) val (= 1 val))))

(s/defn db->model :- (s/maybe models.pelada/Pelada)
  [pelada]
  (when-let [p (some-> pelada misc/unamespace)]
    (medley.core/assoc-some {}
                            :id (:id p)
                            :organization-id (:organization_id p)
                            :organization-name (:organization_name p)
                            :scheduled-at (:scheduled_at p)
                            :num-teams (:num_teams p)
                            :players-per-team (:players_per_team p)
                            :fixed-goalkeepers (db-bool (:fixed_goalkeepers p) false)
                            :home-fixed-goalkeeper-id (:home_fixed_goalkeeper_id p)
                            :away-fixed-goalkeeper-id (:away_fixed_goalkeeper_id p)
                            :notify-casual-players (db-bool (:notify_casual_players p) true)
                            :status (:status p)
                            :closed-at (:closed_at p)
                            :timer-started-at (:timer_started_at p)
                            :timer-accumulated-ms (:timer_accumulated_ms p)
                            :timer-status (:timer_status p)
                            :user-attendance-status (some-> (:user_attendance_status p) (as-> x (if (keyword? x) (name x) (str x)))))))

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

(defn dashboard->response [data]
  (let [pelada (:pelada data)
        is-admin (:is-admin data)
        matches (:matches data)
        teams (:teams data)
        users (:users data)
        organization-players (:organization-players data)
        match-events (:match-events data)
        player-stats (:player-stats data)
        team-players (:team-players data)
        match-lineups (:match-lineups data)
        transactions (:transactions data)
        attendance (:attendance data)

        ;; Transform team-players into a map
        team-players-map (into {} (map (fn [[tid tps]]
                                         [tid (map (fn [tp]
                                                     {:team_id (:team_id tp)
                                                      :player_id (:player_id tp)
                                                      :is_goalkeeper (:is_goalkeeper tp)})
                                                   tps)])
                                       (group-by :team_id team-players)))
        ;; Transform match-lineups into a map of match-id -> team-id -> players
        match-lineups-map (reduce (fn [acc {:keys [match_id team_id] :as lineup}]
                                    (assoc-in acc [match_id team_id] (conj (get-in acc [match_id team_id] []) lineup)))
                                  {}
                                  match-lineups)
        matches-resp (map adapter.match/model->response matches)
        teams-resp (map adapter.team/model->response teams)
        attendance-resp (map adapter.attendance/db->response attendance)
        users-resp (map user->response users)]
    {:pelada (assoc (model->response pelada) :is_admin is-admin)
     :matches matches-resp
     :teams teams-resp
     :users users-resp
     :organization_players (map adapter.player/model->response organization-players)
     :match_events (map adapter.match/event->response match-events)
     :player_stats (when player-stats (map adapter.match/stats->response player-stats))
     :team_players_map team-players-map
     :match_lineups_map match-lineups-map
     :pelada_transactions (map adapter.finance/model->transaction-response transactions)
     :attendance attendance-resp}))
