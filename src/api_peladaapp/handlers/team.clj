(ns api-peladaapp.handlers.team
  (:require
   [api-peladaapp.adapters.team :as adapter.team]
   [api-peladaapp.controllers.pelada :as controller.pelada]
   [api-peladaapp.controllers.team :as controller.team]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [created deleted]]
   [api-peladaapp.logic.authorization :as auth]))

(defn- get-org-id-from-pelada [pelada-id db]
  (:organization-id (controller.pelada/get-pelada pelada-id db)))

(defn- get-org-id-from-team [team-id db]
  (let [team (controller.team/get-team team-id db)]
    (get-org-id-from-pelada (:pelada-id team) db)))

(defn create [request]
  (try (let [db (:database request)
             body (:body request)
             team (adapter.team/create-request->model body)
             user-id (auth/get-user-id-from-request request)
             org-id (get-org-id-from-pelada (:pelada-id team) db)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (controller.team/create-team team db)
             adapter.team/model->response
             created))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete [request]
  (try (let [db (:database request)
             id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             org-id (get-org-id-from-team id db)]
         (auth/require-organization-admin! user-id org-id db)
         (deleted (controller.team/delete-team id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn add-player [request]
  (try (let [db (:database request)
             team-id (Integer/parseInt (str (get-in request [:params :id])))
             player-id (Integer/parseInt (str (get-in request [:body :player_id])))
             user-id (auth/get-user-id-from-request request)
             org-id (get-org-id-from-team team-id db)]
         (auth/require-organization-admin! user-id org-id db)
         (created (controller.team/add-player team-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn remove-player [request]
  (try (let [db (:database request)
             team-id (Integer/parseInt (str (get-in request [:params :id])))
             player-id (Integer/parseInt (str (get-in request [:body :player_id])))
             user-id (auth/get-user-id-from-request request)
             org-id (get-org-id-from-team team-id db)]
         (auth/require-organization-admin! user-id org-id db)
         (deleted (controller.team/remove-player team-id player-id db)))
       (catch Exception e (exception/api-exception-handler e))))