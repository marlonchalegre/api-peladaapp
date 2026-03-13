(ns api-peladaapp.adapters.match
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.match-event :as models.match-event]
   [api-peladaapp.requests.match :as requests.match]
   [api-peladaapp.responses.match :as responses.match]
   [schema.core :as s]))

(defn in->model [{:keys [pelada_id home_team_id away_team_id sequence status home_score away_score
                         timer-started-at timer-accumulated-ms timer-status]}]
  (cond-> {}
    pelada_id (assoc :pelada-id pelada_id)
    home_team_id (assoc :home-team-id home_team_id)
    away_team_id (assoc :away-team-id away_team_id)
    sequence (assoc :sequence sequence)
    status (assoc :status status)
    (some? home_score) (assoc :home-score home_score)
    (some? away_score) (assoc :away-score away_score)
    timer-started-at (assoc :timer-started-at timer-started-at)
    (some? timer-accumulated-ms) (assoc :timer-accumulated-ms timer-accumulated-ms)
    timer-status (assoc :timer-status timer-status)))

(s/defn update-score-request->model :- (s/pred map?)
  [request :- requests.match/UpdateMatchScoreRequest]
  {:home-score (:home_score request)
   :away-score (:away_score request)
   :status (:status request)})

(s/defn create-event-request->model :- (s/pred map?)
  [request :- requests.match/CreateMatchEventRequest]
  (cond-> {:player-id (:player_id request)
           :event-type (:event_type request)}
    (:session_time_ms request) (assoc :session-time-ms (:session_time_ms request))
    (:match_time_ms request) (assoc :match-time-ms (:match_time_ms request))))

(s/defn delete-event-request->model :- (s/pred map?)
  [request :- requests.match/DeleteMatchEventRequest]
  {:player-id (:player_id request)
   :event-type (:event_type request)})

(s/defn add-lineup-request->model :- (s/pred map?)
  [request :- requests.match/AddLineupPlayerRequest]
  {:team-id (:team_id request)
   :player-id (:player_id request)})

(s/defn remove-lineup-request->model :- (s/pred map?)
  [request :- requests.match/RemoveLineupPlayerRequest]
  {:team-id (:team_id request)
   :player-id (:player_id request)})

(s/defn replace-lineup-request->model :- (s/pred map?)
  [request :- requests.match/ReplaceLineupPlayerRequest]
  {:team-id (:team_id request)
   :out-player-id (:out_player_id request)
   :in-player-id (:in_player_id request)})

(s/defn model->response :- responses.match/MatchResponse
  [{:keys [id pelada-id home-team-id away-team-id sequence status home-score away-score
           timer-started-at timer-accumulated-ms timer-status]}]
  (cond-> {:id id
           :pelada_id pelada-id
           :home_team_id home-team-id
           :away_team_id away-team-id
           :sequence sequence}
    status (assoc :status status)
    (some? home-score) (assoc :home_score home-score)
    (some? away-score) (assoc :away_score away-score)
    timer-started-at (assoc :timer_started_at timer-started-at)
    (some? timer-accumulated-ms) (assoc :timer_accumulated_ms timer-accumulated-ms)
    timer-status (assoc :timer_status timer-status)))

(s/defn event->response :- responses.match/MatchEventResponse
  [{:keys [id match-id player-id event-type created-at session-time-ms match-time-ms]}]
  (cond-> {:id id
           :match_id match-id
           :player_id player-id
           :event_type event-type}
    created-at (assoc :created_at created-at)
    session-time-ms (assoc :session_time_ms session-time-ms)
    match-time-ms (assoc :match_time_ms match-time-ms)))

(s/defn stats->response :- responses.match/PlayerStatsResponse
  [{:keys [player-id user-id name goals assists own-goals]}]
  {:player_id player-id
   :user_id user-id
   :name name
   :goals goals
   :assists assists
   :own_goals own-goals})

(s/defn db->model [m]
  (when-let [p (some-> m misc/unamespace)]
    (cond-> {:id (:id p)
             :pelada-id (:pelada_id p)
             :home-team-id (:home_team_id p)
             :away-team-id (:away_team_id p)
             :sequence (:sequence p)}
      (:status p) (assoc :status (:status p))
      (some? (:home_score p)) (assoc :home-score (:home_score p))
      (some? (:away_score p)) (assoc :away-score (:away_score p))
      (:timer_started_at p) (assoc :timer-started-at (:timer_started_at p))
      (some? (:timer_accumulated_ms p)) (assoc :timer-accumulated-ms (:timer_accumulated_ms p))
      (:timer_status p) (assoc :timer-status (:timer_status p)))))

(s/defn db-event->model :- models.match-event/MatchEvent
  [e]
  (when-let [p (some-> e misc/unamespace)]
    (cond-> {:id (:id p)
             :match-id (:match_id p)
             :player-id (:player_id p)
             :event-type (:event_type p)}
      (:created_at p) (assoc :created-at (:created_at p))
      (:session_time_ms p) (assoc :session-time-ms (:session_time_ms p))
      (:match_time_ms p) (assoc :match-time-ms (:match_time_ms p)))))
