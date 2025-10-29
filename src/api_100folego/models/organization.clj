(ns api-100folego.models.organization
  (:require [schema.core :as s]))

(s/defschema Organization
  {:id s/Int
   :name s/Str})
