(ns api-peladaapp.logic.schedule
  (:require
   [api-peladaapp.logic.ils-schedule :as ils]))

(defn schedule-matches-with-limit
  "Schedule matches using ILS (Iterated Local Search)."
  [team-ids matches-per-team]
  (let [n (count team-ids)]
    (if (< n 2)
      []
      (ils/schedule-matches-ils (vec team-ids) matches-per-team))))

(defn schedule-matches
  [team-ids]
  (let [n (count team-ids)
        matches-per-team (dec n)]
    (schedule-matches-with-limit team-ids matches-per-team)))
