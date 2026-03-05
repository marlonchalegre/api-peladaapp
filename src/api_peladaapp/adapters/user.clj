(ns api-peladaapp.adapters.user
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.user :as models.user]
   [api-peladaapp.requests.user :as requests.user]
   [api-peladaapp.responses.user :as responses.user]
   [schema.core :as s]))

(s/defn create-request->model :- models.user/NewUser
  [request :- requests.user/CreateUserRequest]
  (select-keys request [:name :username :email :password :position]))

(s/defn update-request->model :- models.user/UserProfileUpdate
  [request :- requests.user/UpdateUserRequest]
  (select-keys request [:name :username :email :password :position]))

(s/defn update-profile-request->model :- models.user/UserProfileUpdate
  [request :- requests.user/UpdateProfileRequest]
  (select-keys request [:name :username :email :password :position]))

(s/defn model->response :- responses.user/UserResponse
  ([user :- models.user/User]
   (model->response user false))
  ([user :- models.user/User exclude-email? :- s/Bool]
   (let [fields (if exclude-email?
                  [:id :name :username :position :admin-orgs]
                  [:id :name :username :email :position :admin-orgs])]
     (-> (select-keys user fields)
         (update :admin-orgs #(or % []))))))

(s/defn db->model :- models.user/User
  [user]
  (some-> user
          misc/unamespace
          (select-keys [:id :name :username :email :password :position])))
