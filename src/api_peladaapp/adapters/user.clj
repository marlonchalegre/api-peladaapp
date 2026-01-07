(ns api-peladaapp.adapters.user
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.user :as models.user]
   [api-peladaapp.requests.user :as requests.user]
   [api-peladaapp.responses.user :as responses.user]
   [schema.core :as s]))

(s/defn create-request->model :- models.user/NewUser
  [request :- requests.user/CreateUserRequest]
  (select-keys request [:name :email :password]))

(s/defn update-request->model :- models.user/UserProfileUpdate
  [request :- requests.user/UpdateUserRequest]
  (select-keys request [:name :email :password]))

(s/defn update-profile-request->model :- models.user/UserProfileUpdate
  [request :- requests.user/UpdateProfileRequest]
  (select-keys request [:name :email :password]))

(s/defn model->response :- responses.user/UserResponse
  [user :- models.user/User]
  (select-keys user [:id :name :email]))

(s/defn db->model :- models.user/User
  [user]
  (some-> user
          misc/unamespace
          (select-keys [:id :name :email :password])))