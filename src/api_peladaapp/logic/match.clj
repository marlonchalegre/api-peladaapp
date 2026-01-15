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
  [{:keys [home-score away-score status] :as payload}]
  (ensure-non-negative home-score :home-score)
  (ensure-non-negative away-score :away-score)
  (let [update (cond-> {}
                 (some? home-score) (assoc :home-score home-score)
                 (some? away-score) (assoc :away-score away-score)
                 status (assoc :status status))]
    (if (seq update)
      update
      (throw (ex-info "Missing score fields"
                      {:type :bad-request
                       :message "Provide at least one of home_score, away_score or status"
                       :payload payload})))))
