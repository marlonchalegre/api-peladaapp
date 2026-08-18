(ns api-peladaapp.logic.scheduler
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.reminder :as db.reminder]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.grade :as logic.grade]
   [api-peladaapp.logic.notifications :as notifications]
   [clojure.tools.logging :as log])
  (:import
   [java.time Duration ZoneId ZonedDateTime]))

(defn- br-now []
  (ZonedDateTime/now (ZoneId/of "America/Sao_Paulo")))

(defn- should-send-attendance-reminder? [last-sent ^ZonedDateTime now]
  (let [hour (.getHour now)
        ;; Reminders at 10:00 and 18:00
        is-reminder-hour? (or (= hour 10) (= hour 18))]
    (and is-reminder-hour?
         (or (not (seq (str last-sent)))
             (try
               (let [last-sent-inst (helpers.time/->instant last-sent)
                     diff (Duration/between last-sent-inst (.toInstant now))]
                 ;; If last-sent was more than 4 hours ago, we are likely in a new slot (10h vs 18h)
                 (> (.toHours diff) 4))
               (catch Exception _ true))))))

(defn- run-attendance-reminders! [db ^ZonedDateTime now]
  (let [orgs (db.organization/list-organizations db)]
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
                        (notifications/send-notification! org-id :attendance-reminder {:pending-players pending :pelada-id (:id p)} db)
                        (db.reminder/insert-reminder! (:id p) "attendance" db))
                      (log/debug "No pending attendance for pelada" (:id p) "- skipping reminder")))
                  (log/trace "Not the right time or already sent for pelada" (:id p)))))))))))

(defn- reminder-type->db-type [type]
  (case type
    :vote_30m "vote_30m"
    :vote_12h "vote_12h"
    :vote_23h "vote_23h"
    :attendance "attendance"
    :priority_ending "priority_ending"
    :casual_priority_ended "casual_priority_ended"
    :vote_ended "vote_ended"
    (name type)))

(defn- run-priority-ending-reminders! [db ^ZonedDateTime now]
  (let [orgs (db.organization/list-organizations db)]
    (doseq [org orgs]
      (let [org-id (:id org)
            limit-hours (:priority-confirmation-limit-hours org)]
        (when (and (number? limit-hours) (pos? limit-hours) (:waha-attendance-reminder-enabled org))
          (let [active-peladas (filter #(= "attendance" (:status %)) (db.pelada/list-peladas org-id 10 0 db))]
            (doseq [p active-peladas]
              (when-let [scheduled-at (:scheduled-at p)]
                (let [last-sent (db.reminder/get-last-reminder-at (:id p) "priority_ending" db)]
                  (when (nil? last-sent)
                    (let [now-inst (.toInstant now)
                          sched-inst (helpers.time/->instant scheduled-at)
                          remaining-seconds (.toSeconds (Duration/between now-inst sched-inst))
                          limit-seconds (* limit-hours 3600)
                          notice-threshold-seconds (+ limit-seconds (* 2 3600))]
                      (when (and (<= remaining-seconds notice-threshold-seconds)
                                 (> remaining-seconds 0))
                        (let [pending (db.attendance/list-pending-mensalistas-by-pelada (:id p) db)]
                          (log/info "Sending priority ending reminder for pelada" (:id p) "in organization" (:name org))
                          (notifications/send-notification! org-id :priority-ending
                                                            {:pending-players pending
                                                             :pelada-id (:id p)
                                                             :limit-hours limit-hours}
                                                            db)
                          (db.reminder/insert-reminder! (:id p) "priority_ending" db))))))))))))))

(defn- run-casual-priority-ended-reminders! [db ^ZonedDateTime now]
  (let [orgs (db.organization/list-organizations db)]
    (doseq [org orgs]
      (let [org-id (:id org)
            limit-hours (:priority-confirmation-limit-hours org)]
        (when (and (number? limit-hours) (pos? limit-hours) (:waha-attendance-reminder-enabled org))
          (let [active-peladas (filter #(= "attendance" (:status %)) (db.pelada/list-peladas org-id 10 0 db))]
            (doseq [p active-peladas]
              (when-let [scheduled-at (:scheduled-at p)]
                (let [last-sent (db.reminder/get-last-reminder-at (:id p) "casual_priority_ended" db)]
                  (when (nil? last-sent)
                    (let [now-inst (.toInstant now)
                          sched-inst (helpers.time/->instant scheduled-at)
                          remaining-seconds (.toSeconds (Duration/between now-inst sched-inst))
                          limit-seconds (* limit-hours 3600)]
                      (when (and (<= remaining-seconds limit-seconds)
                                 (> remaining-seconds 0))
                        (log/info "Sending casual player priority ended reminder for pelada" (:id p) "in organization" (:name org))
                        (notifications/send-notification! org-id :casual-priority-ended
                                                          {:pelada-id (:id p)
                                                           :scheduled-at scheduled-at
                                                           :limit-hours limit-hours}
                                                          db)
                        (db.reminder/insert-reminder! (:id p) "casual_priority_ended" db)))))))))))))

(defn- check-vote-ended! [db]
  ;; 1. Check for Ended Voting (24h)
  (let [peladas (db.pelada/list-peladas-for-vote-notification db)]
    (if (seq peladas)
      (log/info "Found" (count peladas) "peladas with ended voting period. Processing...")
      (log/debug "No Peladas with ended voting period found."))

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
              (log/info (format "Updating player %s grade: %.2f -> %.2f (perf: %.2f)"
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
                                      (reminder-type->db-type type)
                                      db)))))

(defn execute-tasks! [db]
  (let [now (br-now)]
    (log/info (str "Scheduler tasks execution started at " now))
    (try
      (run-attendance-reminders! db now)
      (run-priority-ending-reminders! db now)
      (run-casual-priority-ended-reminders! db now)
      (check-vote-ended! db)
      (log/info "Scheduler tasks execution finished successfully.")
      (catch Exception e
        (log/error e "Error during scheduler tasks execution")))))
