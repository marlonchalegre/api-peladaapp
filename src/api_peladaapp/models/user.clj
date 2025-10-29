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
