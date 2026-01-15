(ns api-peladaapp.models.match-event
  (:require
   [schema.core :as s]))

(s/defschema MatchEvent
  {:id s/Int
   :match-id s/Int
   :player-id s/Int
   :event-type s/Str
   (s/optional-key :created-at) s/Any})
