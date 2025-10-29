(ns api-peladaapp.models.user
  (:require [schema.core :as s]))

(s/defschema NewUser
  {:name s/Str
   :email s/Str
   :password s/Str})

(s/defschema User
  {:id s/Int
   :name s/Str
   :email s/Str
   :password s/Str})

(s/defschema UserProfileUpdate
  "Schema for user profile updates - excludes score and other protected fields"
  {(s/optional-key :name) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :password) s/Str})
