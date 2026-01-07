(ns api-peladaapp.responses.auth
  (:require
   [api-peladaapp.models.user :as models.user]
   [schema.core :as s]))

(s/defschema AuthResponse
  {:token s/Str
   :user models.user/PublicUser})
