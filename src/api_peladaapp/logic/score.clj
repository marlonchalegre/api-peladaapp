(ns api-peladaapp.logic.score
  (:require
   [api-peladaapp.db.player :as db.player]))

(defn get-normalized-scores
  "Fetches grades for a list of player IDs.
   Returns a map of {player-id grade}.
   Uses the stored grade from \"OrganizationPlayers\", defaulting to 5.0."
  [player-ids db]
  (if (empty? player-ids)
    {}
    (let [db-results (db.player/get-players-grades player-ids db)
          scores-map (reduce (fn [acc {:keys [id grade]}]
                               (let [final-score (double (or grade 5.0))]
                                 (assoc acc id final-score)))
                             {}
                             db-results)]
      (reduce (fn [acc id]
                (if (contains? acc id)
                  acc
                  (assoc acc id 5.0)))
              scores-map
              player-ids))))