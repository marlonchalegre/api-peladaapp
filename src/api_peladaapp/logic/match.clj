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
  [{:keys [home_score away_score status] :as payload}]
  (ensure-non-negative home_score :home_score)
  (ensure-non-negative away_score :away_score)
  (let [update (cond-> {}
                 (some? home_score) (assoc :home_score home_score)
                 (some? away_score) (assoc :away_score away_score)
                 status (assoc :status status))]
    (if (seq update)
      update
      (throw (ex-info "Missing score fields"
                      {:type :bad-request
                       :message "Provide at least one of home_score, away_score or status"
                       :payload payload})))))
