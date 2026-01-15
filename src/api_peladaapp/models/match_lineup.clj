(ns api-peladaapp.models.match-lineup
  (:require
   [schema.core :as s]))

(s/defschema MatchLineupEntry
  {:match-id s/Int
   :team-id s/Int
   :player-id s/Int})
