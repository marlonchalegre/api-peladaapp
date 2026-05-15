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
          identity (:identity request)
          _ (when-not (:is-admin? identity)
              (throw (ex-info "Forbidden: Global admin access required"
                              {:type :forbidden :message "You don't have permission to list all users."})))
          query-params (:query-params request)
          pagination (pagination/parse-pagination-params query-params)
          users-data (controller.user/list-users db pagination)
          users-models (:data users-data)
          users-responses (map adapter.user/model->response users-models)]
      (responses/ok users-responses (:headers users-data)))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn search [request]
  (try
    (let [db (-> request :database)
          identity (:identity request)
          current-user-id (:id identity)
          is-global-admin? (:is-admin? identity)
          query-params (:query-params request)
          query (get query-params "q" "")
          pagination (pagination/parse-pagination-params query-params)
          ;; If not global admin, only search users within shared organizations
          users-data (if is-global-admin?
                       (controller.user/search-users db query pagination)
                       (controller.user/search-users-in-shared-orgs db current-user-id query pagination))
          users-models (:data users-data)
          users-responses (map adapter.user/model->response users-models)]
      (responses/ok users-responses (:headers users-data)))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try
    (let [id (-> request :params :id parse-uuid)
          db (-> request :database)]
      (auth/require-self-or-admin! request id)
      (-> (controller.user/get-user id db)
          (adapter.user/model->response false)
          responses/ok))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn update-profile [request]
  (try
    (let [id (-> request :params :id parse-uuid)
          body (-> request :body)
          db (-> request :database)]
      (auth/require-self-or-admin! request id)
      (-> (adapter.user/update-profile-request->model body)
          (controller.user/update-user-profile id db)
          (adapter.user/model->response false)
          responses/ok))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn delete [request]
  (try
    (let [id (-> request :params :id parse-uuid)
          db (-> request :database)]
      (auth/require-self-or-admin! request id)
      (controller.user/delete-user id db)
      (responses/no-content))
    (catch Exception e
      (exception/api-exception-handler e))))
