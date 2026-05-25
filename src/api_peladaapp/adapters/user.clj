(ns api-peladaapp.adapters.user
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.user :as models.user]
   [api-peladaapp.requests.user :as requests.user]
   [api-peladaapp.responses.user :as responses.user]
   [schema.core :as s]))

(s/defn create-request->model :- models.user/NewUser
  [request :- requests.user/CreateUserRequest]
  (select-keys request [:name :username :email :password :position :phone]))

(s/defn update-request->model :- models.user/UserProfileUpdate
  [request :- requests.user/UpdateUserRequest]
  (-> (select-keys request [:name :username :email :password :position :avatar_filename :phone])
      (misc/rename-key :avatar_filename :avatar-filename)))

(s/defn update-profile-request->model :- models.user/UserProfileUpdate
  [request :- requests.user/UpdateProfileRequest]
  (-> (select-keys request [:name :username :email :password :position :avatar_filename :phone])
      (misc/rename-key :avatar_filename :avatar-filename)))

(s/defn model->response :- responses.user/UserResponse
  ([user :- models.user/User]
   (model->response user true))
  ([user :- models.user/User exclude-email? :- s/Bool]
   (let [fields (if exclude-email?
                  [:id :name :username :position :avatar-filename :avatar_filename :phone :is-super-admin :is-blocked :allow-org-creation]
                  [:id :name :username :email :position :avatar-filename :avatar_filename :phone :is-super-admin :is-blocked :allow-org-creation])]
     (-> (select-keys user fields)
         (assoc :admin_orgs (or (:admin-orgs user) []))
         (misc/rename-key :avatar-filename :avatar_filename)
         (misc/rename-key :is-super-admin :is_super_admin)
         (misc/rename-key :is-blocked :is_blocked)
         (misc/rename-key :allow-org-creation :allow_org_creation)
         (update :is_super_admin #(if (nil? %) false %))
         (update :is_blocked #(if (nil? %) false %))
         (update :allow_org_creation #(if (nil? %) false %))))))

(s/defn db->model :- models.user/User
  [user]
  (some-> user
          misc/unamespace
          (select-keys [:id :name :username :email :password :position :avatar_filename :phone :is_super_admin :is_blocked :allow_org_creation])
          (misc/rename-key :avatar_filename :avatar-filename)
          (misc/rename-key :is_super_admin :is-super-admin)
          (misc/rename-key :is_blocked :is-blocked)
          (misc/rename-key :allow_org_creation :allow-org-creation)
          (update :is-super-admin #(if (nil? %) false %))
          (update :is-blocked #(if (nil? %) false %))
          (update :allow-org-creation #(if (nil? %) false %))))
