(ns api-peladaapp.adapters.admin
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.admin :as models.admin]
   [api-peladaapp.requests.admin :as requests.admin]
   [api-peladaapp.responses.admin :as responses.admin]
   [schema.core :as s]))

(s/defn create-request->model :- models.admin/NewOrganizationAdmin
  [request :- requests.admin/AddAdminRequest
   organization-id :- s/Int]
  {:organization_id organization-id
   :user_id (:user_id request)})

(s/defn model->response :- responses.admin/AdminResponse
  [model :- models.admin/OrganizationAdmin]
  (cond-> (select-keys model [:id :organization_id :user_id :created_at])
    (:user_name model) (assoc :user_name (:user_name model))
    (:user_email model) (assoc :user_email (:user_email model))
    (:organization_name model) (assoc :organization_name (:organization_name model))))

(s/defn db->model [db-admin]
  (some-> db-admin
          misc/unamespace
          (select-keys [:id :organization_id :user_id :created_at :user_name :user_email :organization_name])
          ;; Ensure created_at is string if needed, or keep as is.
          ))