(ns api-peladaapp.logic.vote)

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

(defn validate-vote
  "Ensure vote payload conforms to rules. Returns original vote map."
  [{:keys [voter_id target_id stars] :as vote}]
  (ensure-not-self-vote voter_id target_id)
  (ensure-valid-stars stars)
  vote)

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
