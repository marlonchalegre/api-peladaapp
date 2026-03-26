(ns api-peladaapp.handlers.attendance
  (:require
   [api-peladaapp.controllers.attendance :as controller.attendance]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [ok updated]]
   [api-peladaapp.logic.authorization :as auth]))

(defn update-attendance [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)
             status (:status body)
             target-player-id (some-> (:player_id body) str Integer/parseInt)
             user-id (auth/get-user-id-from-request request)
             pelada (db.pelada/get-pelada pelada-id db)
             org-id (:organization-id pelada)
             current-player (db.player/get-org-player-by-user-id user-id org-id db)]

         (if target-player-id
           ;; If target player id is provided, check if user is admin
           (auth/require-organization-admin! user-id org-id db)
           ;; Otherwise, update current user's attendance
           (when (nil? current-player)
             (throw (ex-info "User is not a player in this organization" {:type :forbidden}))))

         (let [player-id (or target-player-id (:id current-player))
               target-player (if target-player-id
                               (db.player/get-player target-player-id db)
                               current-player)
               final-status (if (and (= status "confirmed")
                                     (not target-player-id)
                                     (not= "mensalista" (:member-type target-player)))
                              "waitlist"
                              status)]
           (updated (controller.attendance/update-attendance pelada-id player-id final-status db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn batch-update-attendance [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)
             status (:status body)
             player-ids (:player_ids body)
             user-id (auth/get-user-id-from-request request)
             pelada (db.pelada/get-pelada pelada-id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (updated (controller.attendance/batch-update-attendance pelada-id player-ids status db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn close-attendance [request]

  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :id])))
             user-id (auth/get-user-id-from-request request)
             pelada (db.pelada/get-pelada pelada-id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (controller.attendance/close-attendance pelada-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-voting-enabled [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (str (get-in request [:params :id])))
             body (:body request)
             enabled? (:enabled body)
             player-id (:player_id body)
             user-id (auth/get-user-id-from-request request)
             pelada (db.pelada/get-pelada pelada-id db)
             org-id (:organization-id pelada)]
         (auth/require-organization-admin! user-id org-id db)
         (ok (controller.attendance/update-voting-enabled pelada-id player-id enabled? db)))
       (catch Exception e (exception/api-exception-handler e))))
