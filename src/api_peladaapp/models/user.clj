(ns api-peladaapp.models.user
  (:require
   [schema.core :as s]))

(s/defschema NewUser
  {:name s/Str
   (s/optional-key :username) s/Str
   (s/optional-key :token) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :password) s/Str
   (s/optional-key :phone) (s/maybe s/Str)
   (s/optional-key :position) (s/enum "Striker" "Midfielder" "Defender" "Goalkeeper")
   (s/optional-key :receive-non-mensalista-updates) s/Bool})

(s/defschema User
  {:id s/Uuid
   (s/optional-key :username) s/Str
   (s/optional-key :name) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :password) s/Str
   (s/optional-key :phone) (s/maybe s/Str)
   (s/optional-key :position) (s/enum "Striker" "Midfielder" "Defender" "Goalkeeper")
   (s/optional-key :admin-orgs) [s/Uuid]
   (s/optional-key :avatar-filename) (s/maybe s/Str)
   (s/optional-key :is-global-admin) s/Bool
   (s/optional-key :is-blocked) s/Bool
   (s/optional-key :allow-org-creation) s/Bool
   (s/optional-key :receive-non-mensalista-updates) s/Bool
   (s/optional-key :stats) (s/maybe {:goals s/Int
                                     :assists s/Int
                                     :matches s/Int})})

(s/defschema UserProfileUpdate
  "Schema for user profile updates - excludes score and other protected fields"
  {(s/optional-key :name) s/Str
   (s/optional-key :username) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :password) s/Str
   (s/optional-key :phone) (s/maybe s/Str)
   (s/optional-key :position) (s/enum "Striker" "Midfielder" "Defender" "Goalkeeper")
   (s/optional-key :avatar-filename) (s/maybe s/Str)
   (s/optional-key :receive-non-mensalista-updates) s/Bool})

(s/defschema PublicUser
  "Schema for user when sensitive fields like password are excluded"
  {:id s/Uuid
   :name s/Str
   (s/optional-key :username) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :phone) (s/maybe s/Str)
   (s/optional-key :position) (s/enum "Striker" "Midfielder" "Defender" "Goalkeeper")
   (s/optional-key :avatar-filename) (s/maybe s/Str)
   (s/optional-key :is-global-admin) s/Bool
   (s/optional-key :is-blocked) s/Bool
   (s/optional-key :allow-org-creation) s/Bool
   (s/optional-key :receive-non-mensalista-updates) s/Bool})


