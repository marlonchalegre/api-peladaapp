(ns api-peladaapp.logic.match)

(def ^:private negative-score-message
  "Placar n?o pode ser negativo")

(defn- ensure-non-negative
  [value field]
  (when (and (some? value) (neg? value))
    (throw (ex-info "Negative score not allowed"
                    {:type :bad-request
                     :message negative-score-message
                     field value}))))

(defn build-score-update
  "Validate and sanitize score update payload. Returns map with non-nil fields."
  [{:keys [home-score away-score status winner-team-id home-team-id away-team-id] :as payload}]
  (ensure-non-negative home-score :home-score)
  (ensure-non-negative away-score :away-score)
  (let [update (cond-> {}
                 (some? home-score) (assoc :home-score home-score)
                 (some? away-score) (assoc :away-score away-score)
                 status (assoc :status status)
                 (contains? payload :winner-team-id) (assoc :winner-team-id winner-team-id)
                 home-team-id (assoc :home-team-id home-team-id)
                 away-team-id (assoc :away-team-id away-team-id))]
    (if (seq update)
      update
      (throw (ex-info "Missing score fields"
                      {:type :bad-request
                       :message "Provide at least one of home_score, away_score or status"
                       :payload payload})))))
