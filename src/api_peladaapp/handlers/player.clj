(ns api-peladaapp.handlers.player
  (:require
   [api-peladaapp.adapters.player :as adapter.player]
   [api-peladaapp.controllers.player :as controller.player]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.responses :refer [created deleted ok]]
   [api-peladaapp.logic.authorization :as auth]))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             player (adapter.player/create-request->model body)
             user-id (auth/get-user-id-from-request request)
             org-id (:organization-id player)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.player/create-player player db)
             adapter.player/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-player-score [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             player-update (adapter.player/update-request->model body)
             user-id (auth/get-user-id-from-request request)
             player (controller.player/get-player id db)
             org-id (:organization-id player)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (adapter.player/model->response (controller.player/update-player id player-update db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             player (controller.player/get-player id db)
             org-id (:organization-id player)]
         (auth/require-organization-admin! user-id org-id db)
         (deleted (controller.player/delete-player id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-by-org [request]
  (try (let [db (:database request)
             org-id (misc/as-uuid (get-in request [:params :organization_id]))
             user-id (auth/get-user-id-from-request request)]
         (auth/require-organization-member! user-id org-id db)
         (ok (map adapter.player/model->response (controller.player/list-players org-id db))))
       (catch Exception e (exception/api-exception-handler e))))
