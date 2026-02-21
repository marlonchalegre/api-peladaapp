(ns api-peladaapp.models.user
  (:require
   [schema.core :as s]))

(s/defschema NewUser
  {:name s/Str
   :email s/Str
   :password s/Str
   (s/optional-key :position) (s/enum "Striker" "Midfielder" "Defender" "Goalkeeper")})

(s/defschema User
  {:id s/Int
   (s/optional-key :name) s/Str
   :email s/Str
   (s/optional-key :password) s/Str
   (s/optional-key :position) (s/enum "Striker" "Midfielder" "Defender" "Goalkeeper")})

(s/defschema UserProfileUpdate
  "Schema for user profile updates - excludes score and other protected fields"
  {(s/optional-key :name) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :password) s/Str
   (s/optional-key :position) (s/enum "Striker" "Midfielder" "Defender" "Goalkeeper")})

(s/defschema PublicUser
  "Schema for user when sensitive fields like password and email are excluded"
  {:id s/Int
   :name s/Str
   (s/optional-key :position) (s/enum "Striker" "Midfielder" "Defender" "Goalkeeper")})
