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
  {:organization-id organization-id
   :user-id (:user_id request)})

(s/defn model->response :- responses.admin/AdminResponse
  [{:keys [id organization-id user-id created-at user_name user_email organization_name] :as model}]
  (cond-> {:id id
           :organization_id organization-id
           :user_id user-id}
    created-at (assoc :created_at created-at)
    user_name (assoc :user_name user_name)
    user_email (assoc :user_email user_email)
    (:user-username model) (assoc :user_username (:user-username model))
    organization_name (assoc :organization_name organization_name)
        ;; Sometimes these are kebab-cased in model if they come from joined results mapped to model
    (:user-name model) (assoc :user_name (:user-name model))
    (:user-email model) (assoc :user_email (:user-email model))
    (:organization-name model) (assoc :organization_name (:organization-name model))))

(s/defn db->model [db-admin]
  (when-let [p (some-> db-admin misc/unamespace)]
    (cond-> {:id (:id p)
             :organization-id (:organization_id p)
             :user-id (:user_id p)}
      (:created_at p) (assoc :created-at (:created_at p))
      (:user_name p) (assoc :user-name (:user_name p))
      (:user_username p) (assoc :user-username (:user_username p))
      (:user_email p) (assoc :user-email (:user_email p))
      (:organization_name p) (assoc :organization-name (:organization_name p)))))
