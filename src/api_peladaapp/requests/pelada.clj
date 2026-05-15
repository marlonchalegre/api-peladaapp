(ns api-peladaapp.requests.pelada
  (:require
   [schema.core :as s]))

(s/defschema CreatePeladaRequest
  {:organization_id s/Uuid
   (s/optional-key :scheduled_at) s/Str
   (s/optional-key :when) s/Str
   (s/optional-key :num_teams) s/Int
   (s/optional-key :players_per_team) s/Int
   (s/optional-key :fixed_goalkeepers) s/Bool
   (s/optional-key :home_fixed_goalkeeper_id) s/Uuid
   (s/optional-key :away_fixed_goalkeeper_id) s/Uuid
   (s/optional-key :status) s/Str})

(s/defschema UpdatePeladaRequest
  {(s/optional-key :organization_id) s/Uuid
   (s/optional-key :scheduled_at) s/Str
   (s/optional-key :when) s/Str
   (s/optional-key :num_teams) s/Int
   (s/optional-key :players_per_team) s/Int
   (s/optional-key :fixed_goalkeepers) s/Bool
   (s/optional-key :home_fixed_goalkeeper_id) (s/maybe s/Uuid)
   (s/optional-key :away_fixed_goalkeeper_id) (s/maybe s/Uuid)
   (s/optional-key :status) s/Str
   (s/optional-key :timer_started_at) (s/maybe s/Any)
   (s/optional-key :timer_accumulated_ms) (s/maybe s/Int)
   (s/optional-key :timer_status) (s/maybe s/Str)})

(s/defschema BeginPeladaRequest
  {(s/optional-key :matches_per_team) s/Int})
