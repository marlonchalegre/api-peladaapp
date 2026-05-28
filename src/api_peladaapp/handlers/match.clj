(ns api-peladaapp.handlers.match
  (:require
   [api-peladaapp.adapters.match :as adapter.match]
   [api-peladaapp.controllers.match :as match-controller]
   [api-peladaapp.controllers.pelada :as pelada-controller]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [ok updated]]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.pelada :as pelada-logic]))

(defn update-score [request]
  (try (let [db (:database request)
             id (parse-uuid (clojure.core/str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)
             match (match-controller/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada-id match) db)
             org-id (:organization-id pelada)]
         ;; Only admins can update scores and finish matches
         (auth/require-organization-admin! user-id org-id db)
         (pelada-logic/ensure-running pelada {:allow-closed? true})
         (-> (match-controller/update-score id (adapter.match/update-score-request->model body) db)
             adapter.match/model->response
             updated))
       (catch Exception e (exception/api-exception-handler e))))

(defn start-timer [request]
  (try (let [db (:database request)
             id (parse-uuid (clojure.core/str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             match (match-controller/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada-id match) db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (match-controller/start-match-timer id db)
             adapter.match/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn pause-timer [request]
  (try (let [db (:database request)
             id (parse-uuid (clojure.core/str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             match (match-controller/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada-id match) db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (match-controller/pause-match-timer id db)
             adapter.match/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn reset-timer [request]
  (try (let [db (:database request)
             id (parse-uuid (clojure.core/str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             match (match-controller/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada-id match) db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (-> (match-controller/reset-match-timer id db)
             adapter.match/model->response
             ok))
       (catch Exception e (exception/api-exception-handler e))))

(defn create-event [request]
  (try (let [db (:database request)
             id (parse-uuid (clojure.core/str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)
             match (match-controller/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada-id match) db)
             org-id (:organization-id pelada)]
         ;; Only admins can create events
         (auth/require-organization-admin! user-id org-id db)
         (pelada-logic/ensure-running pelada {:allow-closed? true})
         (-> (match-controller/create-event id (adapter.match/create-event-request->model body) db)
             adapter.match/event->response
             updated))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete-event [request]
  (try
    (let [db (:database request)
          id (parse-uuid (clojure.core/str (get-in request [:params :id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (match-controller/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      ;; Only admins can perform this action
      (auth/require-organization-admin! user-id org-id db)
      (pelada-logic/ensure-running pelada {:allow-closed? true})
      (updated (match-controller/delete-last-event id (adapter.match/delete-event-request->model body) db)))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn update-event [request]
  (try
    (let [db (:database request)
          id (parse-uuid (clojure.core/str (get-in request [:params :id])))
          event-id (parse-uuid (clojure.core/str (get-in request [:params :event_id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (match-controller/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      (auth/require-organization-admin! user-id org-id db)
      (pelada-logic/ensure-running pelada {:allow-closed? true})
      (-> (match-controller/update-event id event-id (adapter.match/update-event-request->model body) db)
          adapter.match/event->response
          ok))
    (catch Exception e
      (exception/api-exception-handler e))))

;; Lineups (per-match players)
(defn add-lineup-player [request]
  (try
    (let [db (:database request)
          id (parse-uuid (clojure.core/str (get-in request [:params :id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (match-controller/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      ;; Only admins can modify lineups
      (auth/require-organization-admin! user-id org-id db)
      (pelada-logic/ensure-running pelada {:allow-closed? true})
      (updated (match-controller/add-lineup-player id (adapter.match/add-lineup-request->model body) db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn replace-lineup-player [request]
  (try
    (let [db (:database request)
          id (parse-uuid (clojure.core/str (get-in request [:params :id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (match-controller/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      ;; Only admins can modify lineups
      (auth/require-organization-admin! user-id org-id db)
      (pelada-logic/ensure-running pelada {:allow-closed? true})
      (updated (match-controller/replace-lineup-player id (adapter.match/replace-lineup-request->model body) db)))
    (catch Exception e (exception/api-exception-handler e))))
