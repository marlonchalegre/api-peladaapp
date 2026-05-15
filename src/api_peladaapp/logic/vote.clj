(ns api-peladaapp.logic.vote
  (:require
   [api-peladaapp.helpers.time :as time])
  (:import
   [java.time Duration Instant]))

(defn ensure-not-self-vote
  [voter-id target-id]
  (when (= voter-id target-id)
    (throw (ex-info "Self vote not allowed"
                    {:type :bad-request
                     :message "Voter cannot vote for himself"}))))

(defn ensure-valid-stars
  [stars]
  (when-not (and (number? stars) (<= 1 stars 5))
    (throw (ex-info "Invalid vote stars"
                    {:type :bad-request
                     :message "Stars must be 1..5"}))))

(defn ensure-pelada-closed
  "Ensure pelada is closed before voting."
  [pelada]
  (when-not (= "closed" (:status pelada))
    (throw (ex-info "Pelada must be closed to vote"
                    {:type :bad-request
                     :message "Voting is only allowed after pelada is closed"}))))

(defn ensure-voting-window-open
  "Ensure voting is within 24 hours after pelada closed."
  [pelada]
  (let [closed-at (:closed-at pelada)]
    (when-not closed-at
      (throw (ex-info "Pelada has no closed_at timestamp"
                      {:type :bad-request
                       :message "Cannot determine voting window"})))
    (let [closed-instant (time/->instant closed-at)
          now (Instant/now)
          duration (Duration/between closed-instant now)
          limit (Duration/ofHours 24)]
      (when (pos? (.compareTo duration limit))
        (throw (ex-info "Voting window closed"
                        {:type :bad-request
                         :message "Voting is only allowed within 24 hours after pelada closes"}))))))

(defn validate-vote
  "Ensure vote payload conforms to rules. Returns original vote map."
  [{:keys [voter-id target-id stars] :as vote}]
  (ensure-not-self-vote voter-id target-id)
  (ensure-valid-stars stars)
  vote)

(defn validate-voting-eligibility
  "Validate that voting is allowed for this pelada."
  [pelada]
  (ensure-pelada-closed pelada)
  (ensure-voting-window-open pelada)
  pelada)

(defn voting-open?
  "Check if voting is currently open for this pelada."
  [pelada]
  (try
    (validate-voting-eligibility pelada)
    true
    (catch Exception _
      false)))
