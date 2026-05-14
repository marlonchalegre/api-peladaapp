(ns api-peladaapp.adapters.admin
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.admin :as models.admin]
   [api-peladaapp.requests.admin :as requests.admin]
   [api-peladaapp.responses.admin :as responses.admin]
   [schema.core :as s]))

(s/defn create-request->model :- models.admin/NewOrganizationAdmin
  [request :- requests.admin/AddAdminRequest
   organization-id :- s/Any]
  {:organization-id (misc/as-uuid organization-id)
   :user-id (misc/as-uuid (:user_id request))})

(s/defn model->response :- responses.admin/AdminResponse
  [{:keys [id organization-id user-id created-at user-name user-username user-position user-avatar-filename organization-name] :as model}]
  (let [user-username (or user-username (:user-username model))
        user-position (or user-position (:user-position model))
        base (cond-> {:id id
                      :organization_id organization-id
                      :user_id user-id}
               created-at (assoc :created_at created-at)
               user-name (assoc :user_name user-name)
               user-username (assoc :user_username user-username)
               user-position (assoc :user_position user-position)
               organization-name (assoc :organization_name organization-name))]
    (assoc base :user_avatar_filename user-avatar-filename)))

(s/defn db->model [db-admin]
  (when-let [p (some-> db-admin misc/unamespace)]
    (cond-> {:id (:id p)
             :organization-id (:organization_id p)
             :user-id (:user_id p)}
      (:created_at p) (assoc :created-at (:created_at p))
      (:user_name p) (assoc :user-name (:user_name p))
      (or (:user_username p) (:username p)) (assoc :user-username (or (:user_username p) (:username p)))
      (or (:user_position p) (:position p)) (assoc :user-position (or (:user_position p) (:position p)))
      (:avatar_filename p) (assoc :user-avatar-filename (:avatar_filename p))
      (:organization_name p) (assoc :organization-name (:organization_name p)))))
