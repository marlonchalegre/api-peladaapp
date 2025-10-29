(ns api-peladaapp.logic.match-event
  (:require [clojure.string :as str]))

(def ^:private allowed-event-types
  #{"assist" "goal" "own_goal"})

(def ^:private aliases
  {"assistencia" "assist"
   "gol" "goal"
   "gol_contra" "own_goal"
   "gol-contra" "own_goal"})

(defn canonical-type
  "Normalize event type value and ensure it is allowed." 
  [event-type]
  (let [normalized (some-> event-type name str/lower-case)
        canonical (get aliases normalized normalized)]
    (cond
      (nil? normalized)
      (throw (ex-info "Missing event type"
                      {:type :bad-request
                       :message "event_type is required"}))

      (allowed-event-types canonical)
      canonical

      :else
      (throw (ex-info "Invalid event type"
                      {:type :bad-request
                       :message "Invalid event type"
                       :event_type event-type})))))

(defn ensure-player-id
  "Ensure player id is provided." 
  [player-id]
  (if (some? player-id)
    player-id
    (throw (ex-info "Missing player id"
                    {:type :bad-request
                     :message "player_id is required"}))))
