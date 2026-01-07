(ns api-peladaapp.responses.match
  (:require
   [schema.core :as s]))

(s/defschema MatchResponse
  {:id s/Int
   :pelada_id s/Int
   :home_team_id s/Int
   :away_team_id s/Int
   :sequence s/Int
   :status (s/maybe s/Str)
   :home_score (s/maybe s/Int)
   :away_score (s/maybe s/Int)})

(s/defschema MatchEventResponse
  {:id s/Int
   :match_id s/Int
   :player_id s/Int
   :event_type s/Str
   (s/optional-key :created_at) s/Any})

(s/defschema PlayerStatsResponse
  {:player_id s/Int
   :user_id s/Int
   :name s/Str
   :goals s/Int
   :assists s/Int
   :own_goals s/Int})

(s/defschema LineupResponse
  {s/Int [s/Any]}) ;; Map of team_id to list of players (which are maps)
