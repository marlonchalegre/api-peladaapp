(ns api-peladaapp.models.match
  (:require
   [schema.core :as s]))

(s/defschema Match
  {:id s/Uuid
   :pelada-id s/Uuid
   :home-team-id s/Uuid
   :away-team-id s/Uuid
   :sequence s/Int
   :status (s/maybe s/Str)
   :home-score (s/maybe s/Int)
   :away-score (s/maybe s/Int)
   (s/optional-key :timer-started-at) (s/maybe s/Any)
   (s/optional-key :timer-accumulated-ms) (s/maybe s/Int)
   (s/optional-key :timer-status) (s/maybe s/Str)})

(s/defschema PlayerStats
  {:player-id s/Uuid
   :user-id s/Uuid
   :name s/Str
   :goals s/Int
   :assists s/Int
   :own-goals s/Int})
