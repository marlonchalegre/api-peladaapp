(ns api-peladaapp.models.pelada
  (:require
   [schema.core :as s]))

(s/defschema Pelada
  {:id (s/maybe s/Int)
   :organization-id s/Int
   :scheduled-at s/Str
   :status (s/maybe s/Str)
   :num-teams (s/maybe s/Int)
   :players-per-team (s/maybe s/Int)
   :fixed-goalkeepers (s/maybe s/Bool)
   :home-fixed-goalkeeper-id (s/maybe s/Int)
   :away-fixed-goalkeeper-id (s/maybe s/Int)
   :closed-at (s/maybe s/Any)
   (s/optional-key :timer-started-at) (s/maybe s/Any)
   (s/optional-key :timer-accumulated-ms) (s/maybe s/Int)
   (s/optional-key :timer-status) (s/maybe s/Str)
   (s/optional-key :vote-ended-message-sent) (s/maybe s/Bool)
   (s/optional-key :vote-reminder-30m-sent) (s/maybe s/Bool)
   (s/optional-key :vote-reminder-12h-sent) (s/maybe s/Bool)
   (s/optional-key :vote-reminder-23h-sent) (s/maybe s/Bool)
   (s/optional-key :last-attendance-reminder-at) (s/maybe s/Any)})
