(ns api-peladaapp.handlers.global-admin
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :as responses]
   [clojure.string :as str]))

(defn list-organizations [request]
  (try
    (let [db (:database request)
          query-params (:query-params request)
          q (get query-params "q" "")
          pagination-params (pagination/parse-pagination-params query-params)
          orgs-data (if (str/blank? q)
                      (controller.organization/list-organizations db pagination-params)
                      (controller.organization/search-organizations db q pagination-params))
          org-models (:data orgs-data)
          org-responses (map adapter.organization/model->response org-models)]
      (responses/ok org-responses (:headers orgs-data)))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn toggle-organization-block [request]
  (try
    (let [db (:database request)
          org-id (parse-uuid (get-in request [:params :id]))
          org (db.organization/get-organization org-id db)]
      (if org
        (let [new-blocked-state (not (true? (:is-blocked org)))]
          (db.organization/update-organization-flags org-id {:is_blocked new-blocked-state} db)
          (responses/ok {:id org-id
                         :is_blocked new-blocked-state}))
        (responses/not-found {:error "Organization not found"})))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn toggle-user-block [request]
  (try
    (let [db (:database request)
          user-id (parse-uuid (get-in request [:params :id]))
          user (db.user/find-user-by-id user-id db)]
      (if user
        (let [new-blocked-state (not (true? (:is-blocked user)))]
          (db.user/update-user-flags user-id {:is_blocked new-blocked-state} db)
          (responses/ok {:id user-id
                         :is_blocked new-blocked-state}))
        (responses/not-found {:error "User not found"})))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn toggle-user-org-creation [request]
  (try
    (let [db (:database request)
          user-id (parse-uuid (get-in request [:params :id]))
          user (db.user/find-user-by-id user-id db)]
      (if user
        (let [new-allow-org-creation-state (not (true? (:allow-org-creation user)))]
          (db.user/update-user-flags user-id {:allow_org_creation new-allow-org-creation-state} db)
          (responses/ok {:id user-id
                         :allow_org_creation new-allow-org-creation-state}))
        (responses/not-found {:error "User not found"})))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn toggle-user-global-admin [request]
  (try
    (let [db (:database request)
          user-id (parse-uuid (get-in request [:params :id]))
          user (db.user/find-user-by-id user-id db)]
      (if user
        (let [new-global-admin-state (not (true? (:is-global-admin user)))]
          (db.user/update-user-flags user-id {:is_super_admin new-global-admin-state} db)
          (responses/ok {:id user-id
                         :is_super_admin new-global-admin-state}))
        (responses/not-found {:error "User not found"})))
    (catch Exception e
      (exception/api-exception-handler e))))
