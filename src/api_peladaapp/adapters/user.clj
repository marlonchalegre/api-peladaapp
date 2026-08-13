(ns api-peladaapp.adapters.user
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.user :as models.user]
   [api-peladaapp.requests.user :as requests.user]
   [api-peladaapp.responses.user :as responses.user]
   [schema.core :as s]))

(s/defn create-request->model :- models.user/NewUser
  [request :- requests.user/CreateUserRequest]
  (-> (select-keys request [:name :username :email :password :position :phone :receive_non_mensalista_updates])
      (misc/rename-key :receive_non_mensalista_updates :receive-non-mensalista-updates)))

(s/defn update-request->model :- models.user/UserProfileUpdate
  [request :- requests.user/UpdateUserRequest]
  (-> (select-keys request [:name :username :email :password :position :avatar_filename :phone :receive_non_mensalista_updates])
      (misc/rename-key :avatar_filename :avatar-filename)
      (misc/rename-key :receive_non_mensalista_updates :receive-non-mensalista-updates)))

(s/defn update-profile-request->model :- models.user/UserProfileUpdate
  [request :- requests.user/UpdateProfileRequest]
  (update-request->model request))

(s/defn model->response :- responses.user/UserResponse
  ([user :- models.user/User]
   (model->response user true))
  ([user :- models.user/User exclude-email? :- s/Bool]
   (let [fields (if exclude-email?
                  [:id :name :username :position :avatar-filename :avatar_filename :phone :is-global-admin :is-blocked :allow-org-creation :receive-non-mensalista-updates :stats]
                  [:id :name :username :email :position :avatar-filename :avatar_filename :phone :is-global-admin :is-blocked :allow-org-creation :receive-non-mensalista-updates :stats])]
     (-> (select-keys user fields)
         (assoc :admin_orgs (or (:admin-orgs user) []))
         (misc/rename-key :avatar-filename :avatar_filename)
         (misc/rename-key :is-global-admin :is_super_admin)
         (misc/rename-key :is-blocked :is_blocked)
         (misc/rename-key :allow-org-creation :allow_org_creation)
         (misc/rename-key :receive-non-mensalista-updates :receive_non_mensalista_updates)
         (update :is_super_admin true?)
         (update :is_blocked true?)
         (update :allow_org_creation true?)
         (update :receive_non_mensalista_updates true?)))))

(s/defn db->model :- models.user/User
  [user]
  (some-> user
          misc/unamespace
          (select-keys [:id :name :username :email :password :position :avatar_filename :phone :is_super_admin :is_blocked :allow_org_creation :receive_non_mensalista_updates])
          (misc/rename-key :avatar_filename :avatar-filename)
          (misc/rename-key :is_super_admin :is-global-admin)
          (misc/rename-key :is_blocked :is-blocked)
          (misc/rename-key :allow_org_creation :allow-org-creation)
          (misc/rename-key :receive_non_mensalista_updates :receive-non-mensalista-updates)
          (update :is-global-admin true?)
          (update :is-blocked true?)
          (update :allow-org-creation true?)
          (update :receive-non-mensalista-updates true?)))


