(ns api-peladaapp.adapters.admin
  (:require [api-peladaapp.models.admin :as model.admin]
            [schema.core :as s]))

(s/defn db->model [db-admin]
  (when db-admin
    (let [user-name (or (:user_name db-admin) (:Users/user_name db-admin))
          user-email (or (:user_email db-admin) (:Users/user_email db-admin))
          org-name (or (:organization_name db-admin) (:Organizations/organization_name db-admin))]
      (cond-> {:id (:OrganizationAdmins/id db-admin)
               :organization_id (:OrganizationAdmins/organization_id db-admin)
               :user_id (:OrganizationAdmins/user_id db-admin)
               :created_at (str (:OrganizationAdmins/created_at db-admin))}
        user-name (assoc :user_name user-name)
        user-email (assoc :user_email user-email)
        org-name (assoc :organization_name org-name)))))

(s/defn in->model [in]
  {:organization_id (:organization_id in)
   :user_id (:user_id in)})
