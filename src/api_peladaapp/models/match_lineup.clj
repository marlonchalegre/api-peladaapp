(ns api-peladaapp.models.match-lineup
  (:require
   [schema.core :as s]))

(s/defschema MatchLineupEntry
  {:match-id s/Uuid
   :team-id s/Uuid
   :player-id s/Uuid})
