(ns api-peladaapp.handlers.admin
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [api-peladaapp.controllers.admin :as controller.admin]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [created deleted ok]]))

(defn add-admin [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :organization_id])))
             body (:body request)
             admin (adapter.admin/create-request->model body org-id)]
         (-> (controller.admin/add-organization-admin admin db)
             adapter.admin/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-by-id [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (-> (controller.admin/get-organization-admin id db)
             adapter.admin/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn remove-admin [request]
  (try (let [db (:database request)
             id (get-in request [:params :id])]
         (deleted (controller.admin/remove-organization-admin id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn remove-admin-by-org-and-user [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :organization_id])))
             user-id (Integer/parseInt (str (get-in request [:params :user_id])))]
         (deleted (controller.admin/remove-organization-admin-by-org-and-user org-id user-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-organization [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :organization_id])))]
         (ok (map adapter.admin/model->response (controller.admin/list-organization-admins org-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-user [request]
  (try (let [db (:database request)
             user-id (Integer/parseInt (str (get-in request [:params :user_id])))]
         (ok (map adapter.admin/model->response (controller.admin/list-user-admin-organizations user-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn check-is-admin [request]
  (try (let [db (:database request)
             org-id (Integer/parseInt (str (get-in request [:params :organization_id])))
             user-id (Integer/parseInt (str (get-in request [:params :user_id])))]
         (ok {:is_admin (controller.admin/is-user-admin-of-organization? user-id org-id db)}))
       (catch Exception e (exception/api-exception-handler e))))