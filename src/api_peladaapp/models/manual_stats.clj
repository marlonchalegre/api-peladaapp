(ns api-peladaapp.models.manual-stats
  (:require
   [schema.core :as s]))

(s/defschema ManualStats
  {:id (s/maybe s/Int)
   :organization-id s/Int
   :player-id s/Int
   :year s/Int
   :goals (s/maybe s/Int)
   :assists (s/maybe s/Int)
   :own-goals (s/maybe s/Int)
   (s/optional-key :created-at) (s/maybe s/Str)})
