(ns api-peladaapp.requests.auth
  (:require
   [schema.core :as s]))

(s/defschema LoginRequest
  {:email s/Str
   :password s/Str})
