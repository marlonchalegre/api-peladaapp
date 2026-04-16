(ns api-peladaapp.requests.user
  (:require
   [schema.core :as s]))

(s/defschema CreateUserRequest
  {:name s/Str
   :username s/Str
   (s/optional-key :email) s/Str
   :password s/Str
   (s/optional-key :position) s/Str})

(s/defschema UpdateUserRequest
  {(s/optional-key :name) s/Str
   (s/optional-key :username) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :password) s/Str
   (s/optional-key :position) s/Str
   (s/optional-key :avatar_filename) (s/maybe s/Str)})

(s/defschema UpdateProfileRequest
  {(s/optional-key :name) s/Str
   (s/optional-key :username) s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :password) s/Str
   (s/optional-key :position) s/Str
   (s/optional-key :avatar_filename) (s/maybe s/Str)})
