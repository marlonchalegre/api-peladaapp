(ns api-peladaapp.logic.pelada
  (:require [api-peladaapp.logic.schedule :as schedule]))

(defn ensure-open
  "Ensure pelada can start. Returns pelada or throws with :bad-request."
  [pelada]
  (if (= "open" (:status pelada))
    pelada
    (throw (ex-info nil {:type :bad-request
                         :message "Pelada already started or closed"}))))

(defn ensure-schedulable-team-count
  "Ensure there are enough teams and team count is even. Returns team ids."
  [team-ids]
  (let [team-count (count team-ids)]
    (cond
      (< team-count 2)
      (throw (ex-info nil {:type :bad-request
                           :message "At least two teams are required"}))

      (odd? team-count)
      (throw (ex-info nil {:type :bad-request
                           :message "Number of teams must be even"}))

      :else (vec team-ids))))

(defn ensure-startable
  "Validate pelada start preconditions. Returns vector of team ids."
  [pelada team-ids]
  (ensure-open pelada)
  (ensure-schedulable-team-count team-ids))

(defn schedule-matches-for-start
  "Return sequence of match maps {:home :away} honoring optional matches-per-team."
  [team-ids matches-per-team]
  (let [ids (vec team-ids)]
    (if matches-per-team
      (schedule/schedule-matches-with-limit ids matches-per-team)
      (schedule/schedule-matches ids))))

(defn match-plan->rows
  "Convert scheduled matches into DB ready rows."
  [pelada-id scheduled-matches]
  (map-indexed (fn [index {:keys [home away]}]
                 {:pelada_id pelada-id
                  :home_team_id home
                  :away_team_id away
                  :sequence (inc index)
                  :status "scheduled"
                  :home_score 0
                  :away_score 0})
               scheduled-matches))
