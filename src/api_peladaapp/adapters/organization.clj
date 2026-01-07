(ns api-peladaapp.adapters.organization
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.organization :as models.organization]
   [api-peladaapp.requests.organization :as requests.organization]
   [api-peladaapp.responses.organization :as responses.organization]
   [schema.core :as s]))

(s/defn create-request->model :- models.organization/Organization
  [request :- requests.organization/CreateOrganizationRequest]
  (select-keys request [:name]))

(s/defn update-request->model :- models.organization/Organization
  [request :- requests.organization/UpdateOrganizationRequest]
  (select-keys request [:name]))

(s/defn model->response :- responses.organization/OrganizationResponse
  [model :- models.organization/Organization]
  (select-keys model [:id :name]))

(s/defn db->model :- models.organization/Organization
  [o]
  (some-> o misc/unamespace (select-keys [:id :name])))