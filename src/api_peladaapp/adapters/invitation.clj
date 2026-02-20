(ns api-peladaapp.adapters.invitation
  (:require
   [schema.core :as s]))

(s/defn db->model
  [i]
  (when i
    {:id (:id i)
     :organization-id (:organization_id i)
     :organization-name (:organization_name i)
     :email (:email i)
     :token (:token i)
     :status (:status i)
     :created-at (:created_at i)
     :invited-by (:invited_by i)}))

(s/defn model->response
  [m]
  (when m
    {:id (:id m)
     :organization_id (:organization-id m)
     :organization_name (:organization-name m)
     :email (:email m)
     :token (:token m)
     :status (:status m)
     :created_at (:created-at m)
     :invited_by (:invited-by m)}))
