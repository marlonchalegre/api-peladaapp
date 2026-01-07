(ns api-peladaapp.adapters.match
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.match :as models.match]
   [api-peladaapp.models.match-event :as models.match-event]
   [api-peladaapp.requests.match :as requests.match]
   [api-peladaapp.responses.match :as responses.match]
   [schema.core :as s]))

(defn in->model [{:keys [pelada_id home_team_id away_team_id sequence status home_score away_score]}]
  (cond-> {}
    pelada_id (assoc :pelada_id pelada_id)
    home_team_id (assoc :home_team_id home_team_id)
    away_team_id (assoc :away_team_id away_team_id)
    sequence (assoc :sequence sequence)
    status (assoc :status status)
    (some? home_score) (assoc :home_score home_score)
    (some? away_score) (assoc :away_score away_score)))

(s/defn update-score-request->model :- (s/pred map?)
  [request :- requests.match/UpdateMatchScoreRequest]
  (select-keys request [:home_score :away_score]))

(s/defn create-event-request->model :- (s/pred map?)
  [request :- requests.match/CreateMatchEventRequest]
  (select-keys request [:player_id :event_type]))

(s/defn delete-event-request->model :- (s/pred map?)
  [request :- requests.match/DeleteMatchEventRequest]
  (select-keys request [:player_id :event_type]))

(s/defn add-lineup-request->model :- (s/pred map?)
  [request :- requests.match/AddLineupPlayerRequest]
  (select-keys request [:team_id :player_id]))

(s/defn remove-lineup-request->model :- (s/pred map?)
  [request :- requests.match/RemoveLineupPlayerRequest]
  (select-keys request [:team_id :player_id]))

(s/defn replace-lineup-request->model :- (s/pred map?)
  [request :- requests.match/ReplaceLineupPlayerRequest]
  (select-keys request [:team_id :out_player_id :in_player_id]))

(s/defn model->response :- responses.match/MatchResponse
  [m :- models.match/Match]
  (select-keys m [:id :pelada_id :home_team_id :away_team_id :sequence :status :home_score :away_score]))

(s/defn event->response :- responses.match/MatchEventResponse
  [e :- models.match-event/MatchEvent]
  (select-keys e [:id :match_id :player_id :event_type :created_at]))

(s/defn stats->response :- responses.match/PlayerStatsResponse
  [s :- models.match/PlayerStats]
  (select-keys s [:player_id :user_id :name :goals :assists :own_goals]))

(s/defn db->model [m]
  (some-> m misc/unamespace (select-keys [:id :pelada_id :home_team_id :away_team_id :sequence :status :home_score :away_score])))

(s/defn db-event->model :- models.match-event/MatchEvent
  [e]
  (some-> e misc/unamespace (select-keys [:id :match_id :player_id :event_type :created_at])))