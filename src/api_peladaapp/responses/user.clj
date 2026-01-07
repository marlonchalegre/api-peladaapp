(ns api-peladaapp.responses.user
  (:require
   [schema.core :as s]))

(s/defschema UserResponse
  {:id s/Int
   :name s/Str
   :email s/Str})

(s/defschema PublicUserResponse
  {:id s/Int
   :name s/Str})
