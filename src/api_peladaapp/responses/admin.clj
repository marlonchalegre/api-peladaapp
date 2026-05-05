(ns api-peladaapp.responses.admin
  (:require
   [schema.core :as s]))

(s/defschema AdminResponse
  {:id s/Int
   :organization_id s/Int
   :user_id s/Int
   (s/optional-key :user_name) s/Str
   (s/optional-key :user_username) s/Str
   (s/optional-key :user_position) (s/maybe s/Str)
   (s/optional-key :user_avatar_filename) (s/maybe s/Str)
   (s/optional-key :organization_name) s/Str
   (s/optional-key :created_at) s/Any})

(s/defschema IsAdminResponse
  {:is_admin s/Bool})
