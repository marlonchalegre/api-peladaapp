(ns api-peladaapp.handlers.user
  (:require
   [api-peladaapp.adapters.user :as adapter.user]
   [api-peladaapp.controllers.user :as controller.user]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :as responses]
   [api-peladaapp.logic.authorization :as auth]))

(defn- create-action [request]
  (let [body (-> request :body)
        db (-> request :database)]
    (-> body
        adapter.user/create-request->model
        (controller.user/create-user db)
        adapter.user/model->response)))

(defn create [request]
  (try (-> request
           create-action
           responses/created)
       (catch Exception e
         (exception/api-exception-handler e))))

(defn list-all [request]
  (try
    (let [db (-> request :database)
          query-params (:query-params request)
          pagination (pagination/parse-pagination-params query-params)
          ;; Controller should return models. We will handle transformation here.
          ;; Assuming controller.user/list-users logic is updated or we handle existing.
          ;; Currently controller returns a map with :data (users) and pagination headers.
          users-data (controller.user/list-users db pagination)
          users-models (:data users-data)
          users-responses (map adapter.user/model->response users-models)]
      (responses/ok users-responses (:headers users-data)))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn search [request]
  (try
    (let [db (-> request :database)
          query-params (:query-params request)
          query (get query-params "q" "")
          pagination (pagination/parse-pagination-params query-params)
          users-data (controller.user/search-users db query pagination)
          users-models (:data users-data)
          users-responses (map adapter.user/model->response users-models)]
      (responses/ok users-responses (:headers users-data)))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try
    (let [id (-> request :params :id parse-long)
          db (-> request :database)]
      (auth/require-self-or-admin! request id)
      (-> (controller.user/get-user id db)
          adapter.user/model->response
          responses/ok))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn update-profile [request]
  (try
    (let [id (-> request :params :id parse-long)
          body (-> request :body)
          db (-> request :database)]
      (auth/require-self-or-admin! request id)
      (-> (adapter.user/update-profile-request->model body)
          (controller.user/update-user-profile id db)
          adapter.user/model->response
          responses/ok))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn delete [request]
  (try
    (let [id (-> request :params :id parse-long)
          db (-> request :database)]
      (auth/require-self-or-admin! request id)
      (controller.user/delete-user id db)
      (responses/no-content))
    (catch Exception e
      (exception/api-exception-handler e))))
