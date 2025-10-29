(ns api-peladaapp.handlers.match
  (:require [api-peladaapp.controllers.match :as match-controller]
            [api-peladaapp.controllers.pelada :as pelada-controller]
            [api-peladaapp.db.match :as db.match]
            [api-peladaapp.helpers.exception :as exception]
            [api-peladaapp.helpers.responses :refer [ok updated]]
            [api-peladaapp.logic.authorization :as auth]))

(defn list-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (get-in request [:params :pelada_id])
             user-id (auth/get-user-id-from-request request)
             pelada (pelada-controller/get-pelada pelada-id db)
             org-id (:organization_id pelada)]
         ;; Members can view matches
         (auth/require-organization-member! user-id org-id db)
         (ok (match-controller/list-matches pelada-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-events-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (clojure.core/str (get-in request [:params :pelada_id])))
             user-id (auth/get-user-id-from-request request)
             pelada (pelada-controller/get-pelada pelada-id db)
             org-id (:organization_id pelada)]
         ;; Members can view events
         (auth/require-organization-member! user-id org-id db)
         (ok (match-controller/list-events-by-pelada pelada-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-player-stats-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (clojure.core/str (get-in request [:params :pelada_id])))
             user-id (auth/get-user-id-from-request request)
             pelada (pelada-controller/get-pelada pelada-id db)
             org-id (:organization_id pelada)]
         ;; Members can view stats
         (auth/require-organization-member! user-id org-id db)
         (ok (match-controller/list-player-stats-by-pelada pelada-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-score [request]
  (try (let [db (:database request)
             id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)
             match (db.match/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada_id match) db)
             org-id (:organization_id pelada)]
         ;; Only admins can update scores
         (auth/require-organization-admin! user-id org-id db)
         (updated (match-controller/update-score id body db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn create-event [request]
  (try (let [db (:database request)
             id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
             body (:body request)
             user-id (auth/get-user-id-from-request request)
             match (db.match/get-match id db)
             pelada (pelada-controller/get-pelada (:pelada_id match) db)
             org-id (:organization_id pelada)]
         ;; Only admins can create events
         (auth/require-organization-admin! user-id org-id db)
         (updated (match-controller/create-event id body db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete-event [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          body (:body request)
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada_id match) db)
          org-id (:organization_id pelada)]
      ;; Only admins can delete events
      (auth/require-organization-admin! user-id org-id db)
      (updated (match-controller/delete-last-event id body db)))
    (catch Exception e
      (exception/api-exception-handler e))))

;; Lineups (per-match players)
(defn list-lineups [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada_id match) db)
          org-id (:organization_id pelada)]
      ;; Members can view lineups
      (auth/require-organization-member! user-id org-id db)
      (ok (match-controller/list-lineups-by-match id db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn add-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          {:keys [team_id player_id]} (:body request)
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada_id match) db)
          org-id (:organization_id pelada)]
      ;; Only admins can modify lineups
      (auth/require-organization-admin! user-id org-id db)
      (updated (match-controller/add-lineup-player id {:team_id team_id :player_id player_id} db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn remove-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          {:keys [team_id player_id]} (:body request)
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada_id match) db)
          org-id (:organization_id pelada)]
      ;; Only admins can modify lineups
      (auth/require-organization-admin! user-id org-id db)
      (updated (match-controller/remove-lineup-player id {:team_id team_id :player_id player_id} db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn replace-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          {:keys [team_id out_player_id in_player_id]} (:body request)
          user-id (auth/get-user-id-from-request request)
          match (db.match/get-match id db)
          pelada (pelada-controller/get-pelada (:pelada_id match) db)
          org-id (:organization_id pelada)]
      ;; Only admins can modify lineups
      (auth/require-organization-admin! user-id org-id db)
      (updated (match-controller/replace-lineup-player id {:team_id team_id :out_player_id out_player_id :in_player_id in_player_id} db)))
    (catch Exception e (exception/api-exception-handler e))))
