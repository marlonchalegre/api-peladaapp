(ns api-peladaapp.responses.auth
  (:require
   [api-peladaapp.models.user :as models.user]
   [schema.core :as s]))

(s/defschema AuthResponse
  {:token s/Str
   :user (assoc models.user/PublicUser (s/optional-key :admin-orgs) [s/Uuid])})

