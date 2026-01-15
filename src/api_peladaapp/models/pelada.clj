(ns api-peladaapp.models.pelada
  (:require
   [schema.core :as s]))

(s/defschema Pelada
  {:id s/Int
   :organization-id s/Int
   :scheduled-at s/Any
   :num-teams (s/maybe s/Int)
   :players-per-team (s/maybe s/Int)
   :status (s/maybe s/Str)
   :closed-at (s/maybe s/Any)})
