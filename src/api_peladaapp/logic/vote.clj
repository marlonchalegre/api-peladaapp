(ns api-peladaapp.logic.vote
  (:import [java.time Instant Duration]))

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
  (let [closed-at (:closed_at pelada)]
    (when-not closed-at
      (throw (ex-info "Pelada has no closed_at timestamp"
                      {:type :bad-request
                       :message "Cannot determine voting window"})))
    (let [closed-instant (if (instance? Instant closed-at)
                          closed-at
                          ;; Try to parse, adding :00Z if needed for incomplete timestamps
                          (try
                            (Instant/parse (str closed-at))
                            (catch Exception _
                              ;; If parsing fails, try adding :00Z suffix for incomplete ISO timestamps
                              (Instant/parse (str closed-at ":00Z")))))
          now (Instant/now)
          hours-since-close (.toHours (Duration/between closed-instant now))]
      (when (> hours-since-close 24)
        (throw (ex-info "Voting window closed"
                        {:type :bad-request
                         :message "Voting is only allowed within 24 hours after pelada closes"}))))))

(defn validate-vote
  "Ensure vote payload conforms to rules. Returns original vote map."
  [{:keys [voter_id target_id stars] :as vote}]
  (ensure-not-self-vote voter_id target_id)
  (ensure-valid-stars stars)
  vote)

(defn validate-voting-eligibility
  "Validate that voting is allowed for this pelada."
  [pelada]
  (ensure-pelada-closed pelada)
  (ensure-voting-window-open pelada)
  pelada)

(defn normalized-score
  "Normalize player's average stars (1..5) into 1..10 scale." 
  [player-id votes]
  (let [stars (map :stars votes)
        avg (if (seq stars)
              (/ (reduce + stars) (count stars))
              0.0)
        normalized (* 2.0 avg)]
    {:player_id player-id
     :score normalized}))
