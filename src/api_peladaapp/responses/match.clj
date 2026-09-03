(ns api-peladaapp.responses.match
  (:require
   [schema.core :as s]))

(s/defschema MatchResponse
  {:id s/Uuid
   :pelada_id s/Uuid
   :home_team_id s/Uuid
   :away_team_id s/Uuid
   :sequence s/Int
   :status (s/maybe s/Str)
   :home_score (s/maybe s/Int)
   :away_score (s/maybe s/Int)
   (s/optional-key :timer_started_at) (s/maybe s/Any)
   (s/optional-key :timer_accumulated_ms) (s/maybe s/Int)
   (s/optional-key :timer_status) (s/maybe s/Str)})

(s/defschema MatchEventResponse
  {:id s/Uuid
   :match_id s/Uuid
   :player_id s/Uuid
   :event_type s/Str
   (s/optional-key :created_at) s/Any
   (s/optional-key :session_time_ms) (s/maybe s/Int)
   (s/optional-key :match_time_ms) (s/maybe s/Int)
   (s/optional-key :parent_event_id) (s/maybe s/Uuid)
   (s/optional-key :team_id) (s/maybe s/Uuid)})

(s/defschema PlayerStatsResponse
  {:player_id s/Uuid
   :user_id s/Uuid
   :name s/Str
   :goals s/Int
   :assists s/Int
   :own_goals s/Int})

(s/defschema LineupResponse
  {s/Uuid [s/Any]}) ;; Map of team_id to list of players (which are maps)
