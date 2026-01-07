(ns api-peladaapp.models.match
  (:require
   [schema.core :as s]))

(s/defschema Match
  {:id s/Int
   :pelada_id s/Int
   :home_team_id s/Int
   :away_team_id s/Int
   :sequence s/Int
   :status (s/maybe s/Str)
   :home_score (s/maybe s/Int)
   :away_score (s/maybe s/Int)})

(s/defschema PlayerStats
  {:player_id s/Int
   :user_id s/Int
   :name s/Str
   :goals s/Int
   :assists s/Int
   :own_goals s/Int})
