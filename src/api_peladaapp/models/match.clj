(ns api-peladaapp.models.match
  (:require
   [schema.core :as s]))

(s/defschema Match
  {:id s/Int
   :pelada-id s/Int
   :home-team-id s/Int
   :away-team-id s/Int
   :sequence s/Int
   :status (s/maybe s/Str)
   :home-score (s/maybe s/Int)
   :away-score (s/maybe s/Int)})

(s/defschema PlayerStats
  {:player-id s/Int
   :user-id s/Int
   :name s/Str
   :goals s/Int
   :assists s/Int
   :own-goals s/Int})
