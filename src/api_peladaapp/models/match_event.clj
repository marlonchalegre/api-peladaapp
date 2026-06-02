(ns api-peladaapp.models.match-event
  (:require
   [schema.core :as s]))

(s/defschema MatchEvent
  {:id s/Uuid
   :match-id s/Uuid
   :player-id s/Uuid
   :event-type s/Str
   (s/optional-key :created-at) s/Any
   (s/optional-key :session-time-ms) (s/maybe s/Int)
   (s/optional-key :match-time-ms) (s/maybe s/Int)
   (s/optional-key :parent-event-id) (s/maybe s/Uuid)})

