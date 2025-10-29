(ns api-100folego.handlers.user
  (:require
   [api-100folego.adapters.user :as adapter.user]
   [api-100folego.controllers.user :as controller.user]
   [api-100folego.helpers.exception :as exception]
   [api-100folego.helpers.responses :as responses]))

(defn- create-action [request]
  (let [body (-> request :body)
        db (-> request :database)]
    (-> body
        adapter.user/in->model
        (controller.user/create-user db)
        adapter.user/model->out)))

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
        adapter.user/model->out)))

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
        adapter.user/in->model
        (controller.user/update-user user-id db)
        adapter.user/model->out)))

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
    (let [db (-> request :database)]
      (responses/ok (controller.user/list-users db)))
    (catch Exception e
      (exception/api-exception-handler e))))
