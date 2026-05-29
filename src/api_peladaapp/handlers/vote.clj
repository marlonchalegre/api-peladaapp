(ns api-peladaapp.handlers.vote
  (:require
   [api-peladaapp.adapters.vote :as adapter.vote]
   [api-peladaapp.controllers.vote :as controller.vote]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.responses :as responses :refer [ok]]
   [api-peladaapp.logic.authorization :as auth]))

(defn- require-peer-voting-flag! [pelada-id db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        org-id (:organization-id pelada)]
    (when org-id
      (auth/require-feature-flag! org-id :peer_voting db))))

(defn batch-cast [request]
  (try (let [db (:database request)
             pelada-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             _ (require-peer-voting-flag! pelada-id db)
             ;; We need the player-id, not the user-id, for the votes table
             voting-info (controller.vote/get-voting-info pelada-id user-id db)
             voter-player-id (:voter-player-id voting-info)
             body (:body request)
             votes (map (fn [v] {:target-id (misc/as-uuid (:target_id v)) :stars (:stars v)}) (:votes body))]

         (if-not voter-player-id
           (responses/forbidden {:message (or (:message voting-info) "User cannot vote in this pelada")})
           (ok (adapter.vote/batch-vote-model->response
                (controller.vote/batch-cast-votes pelada-id voter-player-id votes db)))))
       (catch Exception e (exception/api-exception-handler e))))

(defn voting-info [request]
  (try (let [db (:database request)
             pelada-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             _ (require-peer-voting-flag! pelada-id db)]
         ;; Use adapter to convert kebab-case internal model to snake_case response
         (ok (adapter.vote/voting-info-model->response
              (controller.vote/get-voting-info pelada-id user-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn voting-results [request]
  (try (let [db (:database request)
             pelada-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)
             _ (require-peer-voting-flag! pelada-id db)]
         (ok (adapter.vote/voting-results-model->response
              (controller.vote/get-voting-results pelada-id user-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn voting-status [request]
  (try (let [db (:database request)
             pelada-id (misc/as-uuid (get-in request [:params :id]))
             _ (require-peer-voting-flag! pelada-id db)]
         (ok (adapter.vote/voting-status-model->response
              (controller.vote/get-voting-status pelada-id db))))
       (catch Exception e (exception/api-exception-handler e))))
