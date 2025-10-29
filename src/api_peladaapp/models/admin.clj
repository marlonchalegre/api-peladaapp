(ns api-peladaapp.models.admin
  (:require [schema.core :as s]))

(s/defschema OrganizationAdmin
  {:id s/Int
   :organization_id s/Int
   :user_id s/Int
   (s/optional-key :created_at) s/Str})

(s/defschema NewOrganizationAdmin
  {:organization_id s/Int
   :user_id s/Int})
