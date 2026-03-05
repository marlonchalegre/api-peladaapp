(ns api-peladaapp.responses.user
  (:require
   [schema.core :as s]))

(s/defschema UserResponse
  {:id s/Int
   :name s/Str
   :username s/Str
   (s/optional-key :email) s/Str
   (s/optional-key :position) (s/maybe s/Str)
   (s/optional-key :admin-orgs) [s/Int]})

(s/defschema PublicUserResponse
  {:id s/Int
   :name s/Str
   :username s/Str
   (s/optional-key :position) (s/maybe s/Str)})

