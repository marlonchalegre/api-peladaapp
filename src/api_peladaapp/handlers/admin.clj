(ns api-peladaapp.handlers.admin
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [api-peladaapp.controllers.admin :as controller.admin]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.responses :refer [created deleted ok]]
   [api-peladaapp.logic.authorization :as auth]))

(defn add-admin [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)
             body (:body request)
             admin (adapter.admin/create-request->model body org-id)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.admin/add-organization-admin admin db)
             adapter.admin/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn remove-admin-by-org-and-user [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             current-user-id (auth/get-user-id-from-request request)
             user-id (misc/as-uuid (get-in request [:params :user_id]))]
         (auth/require-organization-admin! current-user-id org-id db)
         (deleted (controller.admin/remove-organization-admin-by-org-and-user org-id user-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-organization [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id org-id db)
         (ok (map adapter.admin/model->response (controller.admin/list-organization-admins org-id db))))
       (catch Exception e (exception/api-exception-handler e))))
