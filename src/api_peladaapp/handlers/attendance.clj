(ns api-peladaapp.handlers.attendance
  (:require
   [api-peladaapp.controllers.attendance :as controller.attendance]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.responses :refer [ok updated]]
   [api-peladaapp.logic.authorization :as auth]))

(defn update-attendance [request]
  (try (let [db (:database request)
             pelada-id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             status (:status body)
             target-player-id (misc/as-uuid (:player_id body))
             user-id (auth/get-user-id-from-request request)]
         (updated (controller.attendance/update-player-attendance pelada-id user-id target-player-id status db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn batch-update-attendance [request]
  (try (let [db (:database request)
             pelada-id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             status (:status body)
             player-ids (map misc/as-uuid (:player_ids body))
             user-id (auth/get-user-id-from-request request)]
         (updated (controller.attendance/batch-update-attendance pelada-id user-id player-ids status db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn close-attendance [request]
  (try (let [db (:database request)
             pelada-id (misc/as-uuid (get-in request [:params :id]))
             user-id (auth/get-user-id-from-request request)]
         (ok (controller.attendance/close-attendance pelada-id user-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-voting-enabled [request]
  (try (let [db (:database request)
             pelada-id (misc/as-uuid (get-in request [:params :id]))
             body (:body request)
             enabled? (:enabled body)
             player-id (misc/as-uuid (:player_id body))
             user-id (auth/get-user-id-from-request request)]
         (ok (controller.attendance/update-voting-enabled pelada-id user-id player-id enabled? db)))
       (catch Exception e (exception/api-exception-handler e))))
