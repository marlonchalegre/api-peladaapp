(ns api-peladaapp.models.pelada
  (:require [schema.core :as s]))

(s/defschema Pelada
  {:id s/Int
   :organization_id s/Int
   :scheduled_at s/Any
   :num_teams (s/maybe s/Int)
   :players_per_team (s/maybe s/Int)
   :status (s/maybe s/Str)
   :closed_at (s/maybe s/Any)})
