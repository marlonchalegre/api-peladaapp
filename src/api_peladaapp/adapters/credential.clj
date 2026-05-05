(ns api-peladaapp.adapters.credential
  (:require
   [api-peladaapp.adapters.user :as adapters.user]
   [api-peladaapp.models.credential :as models.credential]
   [api-peladaapp.models.user :as models.user]
   [api-peladaapp.requests.auth :as requests.auth]
   [api-peladaapp.responses.auth :as responses.auth]
   [schema.core :as s]))

(s/defn login-request->model :- models.credential/Credential
  [request :- requests.auth/LoginRequest]
  (select-keys request [:email :password]))

(s/defn model->response :- responses.auth/AuthResponse
  [token :- s/Str
   user :- models.user/User]
  {:token token
   :user (adapters.user/model->response user false)})