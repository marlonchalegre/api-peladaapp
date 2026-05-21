(ns api-peladaapp.responses.organization
  (:require
   [schema.core :as s]))

(s/defschema OrganizationResponse
  {:id s/Uuid
   :name s/Str
   (s/optional-key :owner_id) (s/maybe s/Uuid)
   (s/optional-key :role) (s/maybe s/Str)
   (s/optional-key :waha_api_url) (s/maybe s/Str)
   (s/optional-key :waha_instance) (s/maybe s/Str)
   (s/optional-key :waha_group_id) (s/maybe s/Str)
   (s/optional-key :waha_enabled) (s/maybe s/Bool)
   (s/optional-key :waha_start_msg_enabled) (s/maybe s/Bool)
   (s/optional-key :waha_end_msg_enabled) (s/maybe s/Bool)
   (s/optional-key :waha_attendance_reminder_enabled) (s/maybe s/Bool)
   (s/optional-key :waha_vote_reminder_enabled) (s/maybe s/Bool)
   (s/optional-key :waha_vote_ended_msg_enabled) (s/maybe s/Bool)
   (s/optional-key :waha_use_all_mention) (s/maybe s/Bool)})
