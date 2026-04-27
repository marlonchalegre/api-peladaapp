(ns api-peladaapp.logic.scheduler
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.reminder :as db.reminder]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.grade :as logic.grade]
   [api-peladaapp.logic.notifications :as notifications]
   [clojure.tools.logging :as log])
  (:import
   [java.time
    Duration
    Instant
    ZoneId
    ZonedDateTime]))

(defn- br-now []
  (ZonedDateTime/now (ZoneId/of "America/Sao_Paulo")))

(defn- should-send-attendance-reminder? [last-sent ^ZonedDateTime now]
  (let [hour (.getHour now)
        ;; Reminders at 10:00 and 18:00
        is-reminder-hour? (or (= hour 10) (= hour 18))]
    (and is-reminder-hour?
         (or (not (seq (str last-sent)))
             (try
               (let [last-sent-inst (if (instance? Instant last-sent)
                                      last-sent
                                      (Instant/parse (str (clojure.string/replace (str last-sent) " " "T") "Z")))
                     diff (Duration/between last-sent-inst (.toInstant now))]
                 ;; If last-sent was more than 4 hours ago, we are likely in a new slot (10h vs 18h)
                 (> (.toHours diff) 4))
               (catch Exception _ true))))))

(defn- run-attendance-reminders! [db ^ZonedDateTime now]
  (let [orgs (db.organization/list-all-organizations db)]
    (doseq [org orgs]
      (let [org-id (:id org)]
        (when (:waha-attendance-reminder-enabled org)
          (let [active-peladas (filter #(= "attendance" (:status %)) (db.pelada/list-peladas org-id 10 0 db))]
            (doseq [p active-peladas]
              (let [last-sent (db.reminder/get-last-reminder-at (:id p) "attendance" db)]
                (if (should-send-attendance-reminder? last-sent now)
                  (let [pending (db.attendance/list-pending-mensalistas-by-pelada (:id p) db)]
                    (if (seq pending)
                      (do
                        (log/info "Sending automated attendance reminder for pelada" (:id p) "in organization" (:name org))
                        (notifications/send-notification! org-id :attendance-reminder {:pending-players pending} db)
                        (db.reminder/insert-reminder! (:id p) "attendance" db))
                      (log/debug "No pending attendance for pelada" (:id p) "- skipping reminder")))
                  (log/trace "Not the right time or already sent for pelada" (:id p)))))))))))

(defn- check-vote-ended! [db]
  ;; 1. Check for Ended Voting (24h)
  (let [peladas (db.pelada/list-peladas-for-vote-notification db)]
    (if (seq peladas)
      (log/info "Found" (count peladas) "peladas with ended voting period. Processing...")
      (log/debug "No peladas with ended voting period found."))

    (doseq [p peladas]
      (let [org-id (:organization-id p)
            org (db.organization/get-organization org-id db)]
        (log/info "Processing ended voting for pelada" (:id p) "in organization" (:name org))
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

          (if (and org (:waha-vote-ended-msg-enabled org))
            (notifications/send-notification! org-id :vote-ended {:ranking ranking :pelada-id (:id p)} db)
            (log/debug "Voting ended for pelada" (:id p) "but WAHA vote-ended message is disabled for organization" (or (:name org) org-id)))
          (db.reminder/insert-reminder! (:id p) "vote_ended" db)))))

  ;; 2. Check for Vote Reminders (12h and 23h)
  (let [reminders (db.pelada/list-peladas-for-vote-reminders db)]
    (if (seq reminders)
      (log/info "Found" (count reminders) "vote reminders to send. Processing...")
      (log/debug "No pending vote reminders found."))

    (doseq [{:keys [pelada type]} reminders]
      (let [org-id (:organization-id pelada)
            org (db.organization/get-organization org-id db)]
        (if (and org (:waha-vote-reminder-enabled org))
          (let [pending (db.vote/list-pending-voters-by-pelada (:id pelada) db)]
            (if (seq pending)
              (do
                (log/info "Sending" type "vote reminder for pelada" (:id pelada) "in organization" (:name org))
                (notifications/send-notification! org-id :vote-reminder {:pending-voters pending :pelada-id (:id pelada)} db))
              (log/debug "No pending voters for pelada" (:id pelada) "- skipping" type "reminder")))
          (log/debug "Vote reminder" type "due for pelada" (:id pelada) "but disabled for organization" (or (:name org) org-id)))
        (db.reminder/insert-reminder! (:id pelada)
                                      (case type
                                        :30m "vote_30m"
                                        :12h "vote_12h"
                                        :23h "vote_23h")
                                      db)))))
(defn execute-tasks! [db]
  (let [now (br-now)]
    (log/info (str "Scheduler tasks execution started at " now))
    (try
      (run-attendance-reminders! db now)
      (check-vote-ended! db)
      (log/info "Scheduler tasks execution finished successfully.")
      (catch Exception e
        (log/error e "Error during scheduler tasks execution")))))
