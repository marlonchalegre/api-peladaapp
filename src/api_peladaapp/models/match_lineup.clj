(ns api-peladaapp.models.match-lineup
  (:require
   [schema.core :as s]))

(s/defschema MatchLineupEntry
  {:match_id s/Int
   :team_id s/Int
   :player_id s/Int})
