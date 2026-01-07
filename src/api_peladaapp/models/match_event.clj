(ns api-peladaapp.models.match-event
  (:require
   [schema.core :as s]))

(s/defschema MatchEvent
  {:id s/Int
   :match_id s/Int
   :player_id s/Int
   :event_type s/Str
   (s/optional-key :created_at) s/Any})
