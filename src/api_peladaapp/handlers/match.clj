(ns api-peladaapp.handlers.match
  (:require
   [api-peladaapp.adapters.match :as adapter.match]
   [api-peladaapp.controllers.match :as match-controller]
   [api-peladaapp.controllers.pelada :as pelada-controller]
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [ok updated]]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.pelada :as pelada-logic]))

(defn list-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (get-in request [:params :pelada_id])
             user-id (auth/get-user-id-from-request request)
             pelada (pelada-controller/get-pelada pelada-id db)
             org-id (:organization-id pelada)]
         ;; Members can view matches
         (auth/require-organization-member! user-id org-id db)
         (let [matches (match-controller/list-matches pelada-id db)]
           (ok (map adapter.match/model->response matches))))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-events-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (clojure.core/str (get-in request [:params :pelada_id])))
             user-id (auth/get-user-id-from-request request)
             pelada (pelada-controller/get-pelada pelada-id db)
             org-id (:organization-id pelada)]
         ;; Members can view events
         (auth/require-organization-member! user-id org-id db)
         (let [events (match-controller/list-events-by-pelada pelada-id db)]
           (ok (map adapter.match/event->response events))))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-player-stats-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (clojure.core/str (get-in request [:params :pelada_id])))
             user-id (auth/get-user-id-from-request request)
             pelada (pelada-controller/get-pelada pelada-id db)
             org-id (:organization-id pelada)]
         ;; Members can view stats
         (auth/require-organization-member! user-id org-id db)
         (let [stats (match-controller/list-player-stats-by-pelada pelada-id db)]
           (ok (map adapter.match/stats->response stats))))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-score [request]
  (try (let [db (:database request)
             id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)
             match (db.match/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada-id match) db)
             org-id (:organization-id pelada)]
         ;; Only admins can update scores
         (auth/require-organization-admin! user-id org-id db)
         (pelada-logic/ensure-running pelada)
         (-> (match-controller/update-score id (adapter.match/update-score-request->model body) db)
             adapter.match/model->response
             updated))
       (catch Exception e (exception/api-exception-handler e))))

(defn create-event [request]
  (try (let [db (:database request)
             id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)
             match (db.match/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada-id match) db)
             org-id (:organization-id pelada)]
         ;; Only admins can create events
         (auth/require-organization-admin! user-id org-id db)
         (pelada-logic/ensure-running pelada)
         (-> (match-controller/create-event id (adapter.match/create-event-request->model body) db)
             adapter.match/event->response
             updated))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete-event [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      ;; Only admins can delete events
      (auth/require-organization-admin! user-id org-id db)
      (pelada-logic/ensure-running pelada)
      (updated (match-controller/delete-last-event id (adapter.match/delete-event-request->model body) db)))
    (catch Exception e
      (exception/api-exception-handler e))))

;; Lineups (per-match players)
(defn list-lineups [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      ;; Members can view lineups
      (auth/require-organization-member! user-id org-id db)
      (ok (match-controller/list-lineups-by-match id db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn add-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      ;; Only admins can modify lineups
      (auth/require-organization-admin! user-id org-id db)
      (pelada-logic/ensure-running pelada)
      (updated (match-controller/add-lineup-player id (adapter.match/add-lineup-request->model body) db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn remove-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      ;; Only admins can modify lineups
      (auth/require-organization-admin! user-id org-id db)
      (pelada-logic/ensure-running pelada)
      (updated (match-controller/remove-lineup-player id (adapter.match/remove-lineup-request->model body) db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn replace-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada-id match) db)
          org-id (:organization-id pelada)]
      ;; Only admins can modify lineups
      (auth/require-organization-admin! user-id org-id db)
      (pelada-logic/ensure-running pelada)
      (updated (match-controller/replace-lineup-player id (adapter.match/replace-lineup-request->model body) db)))
    (catch Exception e (exception/api-exception-handler e))))