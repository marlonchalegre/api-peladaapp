(ns api-peladaapp.models.admin
  (:require
   [schema.core :as s]))

(s/defschema OrganizationAdmin
  {:id s/Int
   :organization-id s/Int
   :user-id s/Int
   (s/optional-key :user-name) s/Str
   (s/optional-key :user-username) s/Str
   (s/optional-key :user-email) s/Str
   (s/optional-key :user-position) (s/maybe s/Str)
   (s/optional-key :organization-name) s/Str
   (s/optional-key :created-at) s/Str})

(s/defschema NewOrganizationAdmin
  {:organization-id s/Int
   :user-id s/Int})
