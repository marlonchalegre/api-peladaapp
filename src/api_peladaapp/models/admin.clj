(ns api-peladaapp.models.admin
  (:require
   [schema.core :as s]))

(s/defschema OrganizationAdmin
  {:id s/Int
   :organization-id s/Int
   :user-id s/Int
   (s/optional-key :created-at) s/Str})

(s/defschema NewOrganizationAdmin
  {:organization-id s/Int
   :user-id s/Int})
