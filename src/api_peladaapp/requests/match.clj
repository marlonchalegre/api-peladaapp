(ns api-peladaapp.requests.match
  (:require
   [schema.core :as s]))

(s/defschema UpdateMatchScoreRequest
  {:home_score s/Int
   :away_score s/Int
   (s/optional-key :status) s/Str})

(s/defschema CreateMatchEventRequest
  {:player_id s/Int
   :event_type s/Str
   (s/optional-key :session_time_ms) (s/maybe s/Int)
   (s/optional-key :match_time_ms) (s/maybe s/Int)})

(s/defschema DeleteMatchEventRequest
  {:player_id s/Int
   :event_type s/Str})

(s/defschema AddLineupPlayerRequest
  {:team_id s/Int
   :player_id s/Int})

(s/defschema RemoveLineupPlayerRequest
  {:team_id s/Int
   :player_id s/Int})

(s/defschema ReplaceLineupPlayerRequest
  {:team_id s/Int
   :out_player_id s/Int
   :in_player_id s/Int})
