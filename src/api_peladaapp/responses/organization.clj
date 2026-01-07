(ns api-peladaapp.responses.organization
  (:require
   [schema.core :as s]))

(s/defschema OrganizationResponse
  {:id s/Int
   :name s/Str})
