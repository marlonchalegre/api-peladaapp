(ns api-peladaapp.requests.admin
  (:require
   [schema.core :as s]))

(s/defschema AddAdminRequest
  {:user_id s/Uuid})
