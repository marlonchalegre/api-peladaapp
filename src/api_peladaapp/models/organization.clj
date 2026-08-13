(ns api-peladaapp.models.organization
  (:require
   [schema.core :as s]))

(s/defschema Organization
  {:id s/Uuid
   :name s/Str
   (s/optional-key :owner-id) (s/maybe s/Uuid)
   (s/optional-key :priority-confirmation-limit-hours) (s/maybe s/Int)
   (s/optional-key :waha-api-url) (s/maybe s/Str)
   (s/optional-key :waha-instance) (s/maybe s/Str)
   (s/optional-key :waha-group-id) (s/maybe s/Str)
   (s/optional-key :waha-enabled) (s/maybe s/Bool)
   (s/optional-key :waha-start-msg-enabled) (s/maybe s/Bool)
   (s/optional-key :waha-end-msg-enabled) (s/maybe s/Bool)
   (s/optional-key :waha-attendance-reminder-enabled) (s/maybe s/Bool)
   (s/optional-key :waha-vote-reminder-enabled) (s/maybe s/Bool)
   (s/optional-key :waha-vote-ended-msg-enabled) (s/maybe s/Bool)
   (s/optional-key :waha-use-all-mention) (s/maybe s/Bool)
   (s/optional-key :is-blocked) s/Bool})
