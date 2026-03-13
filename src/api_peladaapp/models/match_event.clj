(ns api-peladaapp.models.match-event
  (:require
   [schema.core :as s]))

(s/defschema MatchEvent
  {:id s/Int
   :match-id s/Int
   :player-id s/Int
   :event-type s/Str
   (s/optional-key :created-at) s/Any
   (s/optional-key :session-time-ms) (s/maybe s/Int)
   (s/optional-key :match-time-ms) (s/maybe s/Int)})
