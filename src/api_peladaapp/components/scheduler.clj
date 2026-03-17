(ns api-peladaapp.components.scheduler
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.grade :as logic.grade]
   [api-peladaapp.logic.notifications :as notifications]
   [chime.core :as chime]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component])
  (:import
   [java.time
    Duration
    Instant
    ZoneId
    ZonedDateTime]))

(defn- br-now []
  (ZonedDateTime/now (ZoneId/of "America/Sao_Paulo")))

(defn- br-reminders-seq []
  (let [now (br-now)]
    (->> (iterate #(.plusDays % 1) now)
         (mapcat (fn [d]
                   [(-> d (.withHour 10) (.withMinute 0) (.withSecond 0) (.withNano 0) .toInstant)
                    (-> d (.withHour 18) (.withMinute 0) (.withSecond 0) (.withNano 0) .toInstant)]))
         (filter #(.isAfter % (.toInstant now))))))

(defn- run-reminders! [db]
  (log/info "Running automated attendance reminders...")
  (let [orgs (db.organization/list-all-organizations db)]
    (doseq [org orgs]
      (let [org-id (:id org)]
        ;; Attendance reminders
        (when (:waha-attendance-reminder-enabled org)
          (let [active-peladas (filter #(= "attendance" (:status %)) (db.pelada/list-peladas org-id 10 0 db))]
            (doseq [p active-peladas]
              (let [pending (db.attendance/list-pending-attendance-by-pelada (:id p) db)]
                (when (seq pending)
                  (notifications/send-notification! org-id :attendance-reminder {:pending-players pending} db))))))))))

(defn- check-vote-ended! [db]
  (log/info "Checking for ended voting periods and reminders...")
  ;; 1. Check for Ended Voting (24h)
  (let [peladas (db.pelada/list-peladas-for-vote-notification db)]
    (doseq [p peladas]
      (let [org-id (:organization-id p)
            org (db.organization/get-organization org-id db)]
        (when (and org (:waha-vote-ended-msg-enabled org))
          (let [ranking (db.vote/list-ranking-by-pelada (:id p) db)]
            ;; Automated Grade Update
            (doseq [r ranking]
              (let [player-id (:player-id r)
                    avg-stars (:avg-stars r)
                    performance (logic.grade/performance-from-stars avg-stars)
                    current-player (db.player/get-player player-id db)
                    current-grade (or (:grade current-player) 5.0)
                    new-grade (logic.grade/calculate-new-grade current-grade performance)]
                (log/info (format "Updating player %d grade: %.2f -> %.2f (perf: %.2f)"
                                  player-id current-grade new-grade performance))
                (db.player/update-player-grade player-id new-grade db)))

            (notifications/send-notification! org-id :vote-ended {:ranking ranking} db)
            (db.pelada/update-pelada (:id p) {:vote-ended-message-sent true} db))))))

  ;; 2. Check for Vote Reminders (12h and 23h)
  (let [reminders (db.pelada/list-peladas-for-vote-reminders db)]
    (doseq [{:keys [pelada type]} reminders]
      (let [org-id (:organization-id pelada)
            org (db.organization/get-organization org-id db)]
        (when (and org (:waha-vote-reminder-enabled org))
          (let [pending (db.vote/list-pending-voters-by-pelada (:id pelada) db)]
            (when (seq pending)
              (notifications/send-notification! org-id :vote-reminder {:pending-voters pending} db))
            (db.pelada/update-pelada (:id pelada)
                                     (if (= type :12h)
                                       {:vote-reminder-12h-sent true}
                                       {:vote-reminder-23h-sent true})
                                     db)))))))

(defrecord Scheduler [database reminder-chime vote-ended-chime]
  component/Lifecycle
  (start [this]
    (log/info "Starting Scheduler component...")
    (let [db (-> this :database :database)]
      (assoc this
             :reminder-chime (chime/chime-at (br-reminders-seq)
                                             (fn [_] (run-reminders! db)))
             :vote-ended-chime (chime/chime-at (chime/periodic-seq (Instant/now) (Duration/ofHours 1))
                                               (fn [_] (check-vote-ended! db))))))
  (stop [this]
    (log/info "Stopping Scheduler component...")
    (when reminder-chime (.close reminder-chime))
    (when vote-ended-chime (.close vote-ended-chime))
    (assoc this :reminder-chime nil :vote-ended-chime nil)))

(defn new-scheduler []
  (component/using
   (map->Scheduler {})
   [:database]))
