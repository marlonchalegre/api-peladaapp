(ns api-peladaapp.responses.user
  (:require
   [schema.core :as s]))

(s/defschema UserResponse
  {:id s/Uuid
   :name s/Str
   :username s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :phone) (s/maybe s/Str)
   (s/optional-key :position) (s/maybe s/Str)
   (s/optional-key :admin_orgs) [s/Uuid]
   (s/optional-key :avatar_filename) (s/maybe s/Str)
   (s/optional-key :is_super_admin) s/Bool
   (s/optional-key :is_blocked) s/Bool
   (s/optional-key :allow_org_creation) s/Bool})

(s/defschema PublicUserResponse
  {:id s/Uuid
   :name s/Str
   :username s/Str
   (s/optional-key :phone) (s/maybe s/Str)
   (s/optional-key :position) (s/maybe s/Str)
   (s/optional-key :avatar_filename) (s/maybe s/Str)
   (s/optional-key :is_super_admin) s/Bool
   (s/optional-key :is_blocked) s/Bool
   (s/optional-key :allow_org_creation) s/Bool})


