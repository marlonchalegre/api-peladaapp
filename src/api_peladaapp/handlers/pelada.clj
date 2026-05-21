(ns api-peladaapp.handlers.pelada
  (:refer-clojure :exclude [update])
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [api-peladaapp.controllers.pelada :as controller.pelada]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.responses :refer [created deleted ok]]
   [api-peladaapp.logic.authorization :as auth]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             pelada (adapter.pelada/create-request->model body)
             user-id (auth/get-user-id-from-request request)
             org-id (:organization-id pelada)]
         ;; Only admins can create peladas
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.pelada/create-pelada pelada db)
             adapter.pelada/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-full-details [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             pelada-data (controller.pelada/get-pelada-full-details-controller id user-id db)
             pelada (:pelada pelada-data)
             org-id (or (:organization_id pelada) (:organization-id pelada))]
         ;; Members can view peladas
         (auth/require-organization-member! user-id org-id db)
         (ok (adapter.pelada/full-details->response pelada-data)))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         ;; Only admins can delete peladas
         (auth/require-organization-admin! user-id org-id db)
         (deleted (controller.pelada/delete-pelada id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-org [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)
             query-params (:query-params request)
             pagination (pagination/parse-pagination-params query-params)]
         ;; Members can list peladas
         (auth/require-organization-member! user-id org-id db)
         (let [peladas-data (controller.pelada/list-peladas org-id db pagination)
               peladas-models (:data peladas-data)
               peladas-responses (map adapter.pelada/model->response peladas-models)]
           (ok peladas-responses (:headers peladas-data))))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-user [request]
  (try (let [db (:database request)
             user-id-param (misc/as-uuid (get-in request [:params :user_id]))
             auth-user-id (auth/get-user-id-from-request request)
             query-params (:query-params request)
             pagination (pagination/parse-pagination-params query-params)]
         ;; Users can list their own peladas
         (when (not= user-id-param auth-user-id)
           (throw (ex-info "Unauthorized" {:type :forbidden :message "You can only list your own peladas"})))

         (let [peladas-data (controller.pelada/list-peladas-by-user user-id-param db pagination)
               peladas-models (:data peladas-data)
               peladas-responses (map adapter.pelada/model->response peladas-models)]
           (ok peladas-responses (:headers peladas-data))))
       (catch Exception e (exception/api-exception-handler e))))

(defn begin [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             matches-per-team (some-> (:matches_per_team body) int)
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         ;; Only admins can begin peladas
         (auth/require-organization-admin! user-id org-id db)
         (ok (adapter.pelada/begin-model->response
              (if matches-per-team
                (controller.pelada/begin-pelada id db {:matches_per_team matches-per-team})
                (controller.pelada/begin-pelada id db)))))
       (catch Exception e (exception/api-exception-handler e))))

(defn close [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         ;; Only admins can close peladas
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.pelada/close-pelada id db)
             adapter.pelada/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn start-timer [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.pelada/start-pelada-timer id db)
             adapter.pelada/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn pause-timer [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.pelada/pause-pelada-timer id db)
             adapter.pelada/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn reset-timer [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.pelada/reset-pelada-timer id db)
             adapter.pelada/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-schedule-preview [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             matches-per-team (Integer/parseInt (str (or (get-in request [:params :matches_per_team])
                                                         (get-in request [:params "matches_per_team"])
                                                         "2")))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (controller.pelada/get-schedule-preview id matches-per-team db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn save-schedule-plan [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             matches (map (fn [m] (assoc m :home (misc/as-uuid (:home m)) :away (misc/as-uuid (:away m)))) (:matches body))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (controller.pelada/save-schedule-plan id matches db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-schedule-plan [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (controller.pelada/get-schedule-plan id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn get-dashboard-data [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         ;; Members can view peladas
         (auth/require-organization-member! user-id org-id db)
         (ok (adapter.pelada/dashboard->response (controller.pelada/get-pelada-dashboard-data id user-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn update [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             pelada-update (adapter.pelada/update-request->model body)
             user-id (auth/get-user-id-from-request request)
             pelada (controller.pelada/get-pelada id db)
             org-id (:organization-id pelada)]
         ;; Only admins can update peladas
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.pelada/update-pelada id pelada-update db)
             adapter.pelada/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))