(ns api-peladaapp.adapters.invitation
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [medley.core :as medley.core]
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

(defn payload->invitation [payload]
  (when payload
    (let [p (misc/unamespace payload)]
      (medley.core/assoc-some {}
                              :id (misc/as-uuid (:id p))
                              :organization-id (misc/as-uuid (:organization_id p))
                              :email (:email p)
                              :token (:token p)
                              :invited-by (misc/as-uuid (:invited_by p))))))
