(ns api-peladaapp.requests.match
  (:require
   [schema.core :as s]))

(s/defschema UpdateMatchScoreRequest
  {:home_score s/Int
   :away_score s/Int
   (s/optional-key :status) s/Str})

(s/defschema CreateMatchEventRequest
  {:player_id s/Uuid
   :event_type s/Str
   (s/optional-key :session_time_ms) (s/maybe s/Int)
   (s/optional-key :match_time_ms) (s/maybe s/Int)
   (s/optional-key :assistant_id) (s/maybe s/Uuid)
   (s/optional-key :team_id) (s/maybe s/Uuid)})

(s/defschema DeleteMatchEventRequest
  {:player_id s/Uuid
   :event_type s/Str
   (s/optional-key :id) (s/maybe s/Uuid)})

(s/defschema UpdateMatchEventRequest
  {:player_id s/Uuid
   (s/optional-key :assistant_id) (s/maybe s/Uuid)})

(s/defschema AddLineupPlayerRequest
  {:team_id s/Uuid
   :player_id s/Uuid})

(s/defschema RemoveLineupPlayerRequest
  {:team_id s/Uuid
   :player_id s/Uuid})

(s/defschema ReplaceLineupPlayerRequest
  {:team_id s/Uuid
   :out_player_id s/Uuid
   :in_player_id s/Uuid})
