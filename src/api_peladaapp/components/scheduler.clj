(ns api-peladaapp.components.scheduler
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.notifications :as notifications]
   [api-peladaapp.logic.vote :as logic.vote]
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
  (log/info "Running automated reminders...")
  (let [orgs (db.organization/list-all-organizations db)]
    (doseq [org orgs]
      (let [org-id (:id org)]
        ;; Attendance reminders
        (when (:waha-attendance-reminder-enabled org)
          (let [active-peladas (filter #(= "attendance" (:status %)) (db.pelada/list-peladas org-id 10 0 db))]
            (doseq [p active-peladas]
              (let [pending (db.attendance/list-pending-attendance-by-pelada (:id p) db)]
                (when (seq pending)
                  (notifications/send-notification! org-id :attendance-reminder {:pending-players pending} db))))))

        ;; Vote reminders
        (when (:waha-vote-reminder-enabled org)
          (let [closed-peladas (filter #(= "closed" (:status %)) (db.pelada/list-peladas org-id 10 0 db))]
            (doseq [p closed-peladas]
              (when (logic.vote/voting-open? p)
                (let [pending (db.vote/list-pending-voters-by-pelada (:id p) db)]
                  (when (seq pending)
                    (notifications/send-notification! org-id :vote-reminder {:pending-voters pending} db)))))))))))

(defn- check-vote-ended! [db]
  (log/info "Checking for ended voting periods...")
  (let [orgs (db.organization/list-all-organizations db)]
    (doseq [org orgs]
      (let [org-id (:id org)]
        (when (:waha-vote-ended-msg-enabled org)
          (let [peladas (filter (fn [p] (and (= "closed" (:status p))
                                             (not (:vote-ended-message-sent p))
                                             (not (logic.vote/voting-open? p))))
                                (db.pelada/list-peladas org-id 10 0 db))]
            (doseq [p peladas]
              (let [ranking (db.vote/list-ranking-by-pelada (:id p) db)]
                (notifications/send-notification! org-id :vote-ended {:ranking ranking} db)
                (db.pelada/update-pelada (:id p) {:vote-ended-message-sent true} db)))))))))

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
