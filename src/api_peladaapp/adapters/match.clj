(ns api-peladaapp.adapters.match
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.match-event :as models.match-event]
   [api-peladaapp.requests.match :as requests.match]
   [api-peladaapp.responses.match :as responses.match]
   [schema.core :as s]))

(defn in->model [{:keys [pelada_id home_team_id away_team_id sequence status home_score away_score]}]
  (cond-> {}
    pelada_id (assoc :pelada-id pelada_id)
    home_team_id (assoc :home-team-id home_team_id)
    away_team_id (assoc :away-team-id away_team_id)
    sequence (assoc :sequence sequence)
    status (assoc :status status)
    (some? home_score) (assoc :home-score home_score)
    (some? away_score) (assoc :away-score away_score)))

(s/defn update-score-request->model :- (s/pred map?)
  [request :- requests.match/UpdateMatchScoreRequest]
  {:home-score (:home_score request)
   :away-score (:away_score request)})

(s/defn create-event-request->model :- (s/pred map?)
  [request :- requests.match/CreateMatchEventRequest]
  {:player-id (:player_id request)
   :event-type (:event_type request)})

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
  [{:keys [id pelada-id home-team-id away-team-id sequence status home-score away-score]}]
  (cond-> {:id id
           :pelada_id pelada-id
           :home_team_id home-team-id
           :away_team_id away-team-id
           :sequence sequence}
    status (assoc :status status)
    (some? home-score) (assoc :home_score home-score)
    (some? away-score) (assoc :away_score away-score)))

(s/defn event->response :- responses.match/MatchEventResponse
  [{:keys [id match-id player-id event-type created-at]}]
  (cond-> {:id id
           :match_id match-id
           :player_id player-id
           :event_type event-type}
    created-at (assoc :created_at created-at)))

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
      (some? (:away_score p)) (assoc :away-score (:away_score p)))))

(s/defn db-event->model :- models.match-event/MatchEvent
  [e]
  (when-let [p (some-> e misc/unamespace)]
    (cond-> {:id (:id p)
             :match-id (:match_id p)
             :player-id (:player_id p)
             :event-type (:event_type p)}
      (:created_at p) (assoc :created-at (:created_at p)))))