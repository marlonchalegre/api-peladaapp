(ns api-peladaapp.models.organization
  (:require [schema.core :as s]))

(s/defschema Organization
  {:id s/Int
   :name s/Str})
