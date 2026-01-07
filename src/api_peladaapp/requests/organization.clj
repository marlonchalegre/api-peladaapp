(ns api-peladaapp.requests.organization
  (:require
   [schema.core :as s]))

(s/defschema CreateOrganizationRequest
  {:name s/Str})

(s/defschema UpdateOrganizationRequest
  {:name s/Str})
