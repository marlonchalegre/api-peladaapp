(ns api-peladaapp.models.manual-stats
  (:require
   [schema.core :as s]))

(s/defschema ManualStats
  {:id (s/maybe s/Uuid)
   :organization-id s/Uuid
   :player-id s/Uuid
   :year s/Int
   :goals (s/maybe s/Int)
   :assists (s/maybe s/Int)
   :own-goals (s/maybe s/Int)
   (s/optional-key :created-at) (s/maybe s/Str)})
