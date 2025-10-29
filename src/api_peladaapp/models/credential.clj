(ns api-peladaapp.models.credential 
  (:require
   [schema.core :as s]))

(s/defschema Credential
  {:email s/Str
   :password s/Str})
