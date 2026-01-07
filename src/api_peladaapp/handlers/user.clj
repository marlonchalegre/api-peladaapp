(ns api-peladaapp.handlers.user
  (:require
   [api-peladaapp.adapters.user :as adapter.user]
   [api-peladaapp.controllers.user :as controller.user]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :as responses]))

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

(defn- get-action [request]
  (let [user-id (-> request :params :id)
        db (-> request :database)]
    (-> (controller.user/get-user user-id db)
        adapter.user/model->response)))

(defn get-by-id [request]
  (try
    (-> request
        get-action
        responses/ok)
    (catch Exception e
      (exception/api-exception-handler e))))

(defn- update-action [request]
  (let [user-id (-> request :params :id)
        body (-> request :body)
        db (-> request :database)]
    (-> body
        adapter.user/update-request->model
        (controller.user/update-user user-id db)
        adapter.user/model->response)))

(defn update-by-id [request]
  (try (-> request
           update-action
           responses/updated)
       (catch Exception e
         (exception/api-exception-handler e))))

(defn- delete-action [request]
  (let [user-id (-> request :params :id)
        db (-> request :database)]
    (controller.user/delete-user user-id db)))

(defn delete [request]
  (try (-> request
           delete-action
           responses/deleted)
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

(defn- update-profile-action [request]
  (let [user-id (-> request :params :id Integer/parseInt)
        authenticated-user-id (-> request :identity :id)
        body (-> request :body)
        db (-> request :database)]
    ;; Authorization check: user can only update their own profile
    (when (not= user-id authenticated-user-id)
      (throw (ex-info nil {:type :forbidden :message "You can only update your own profile"})))
    ;; The controller will handle checking if user exists
    (-> body
        adapter.user/update-profile-request->model
        (controller.user/update-user-profile user-id db)
        adapter.user/model->response)))

(defn update-profile [request]
  (try (-> request
           update-profile-action
           responses/updated)
       (catch Exception e
         (exception/api-exception-handler e))))