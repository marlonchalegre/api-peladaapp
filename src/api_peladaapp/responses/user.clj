(ns api-peladaapp.responses.user
  (:require
   [schema.core :as s]))

(s/defschema UserResponse
  {:id s/Int
   :name s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :position) s/Str})

(s/defschema PublicUserResponse
  {:id s/Int
   :name s/Str
   (s/optional-key :position) s/Str})
