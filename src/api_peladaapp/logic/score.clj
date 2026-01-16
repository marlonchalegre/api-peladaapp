(ns api-peladaapp.logic.score
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(defn get-normalized-scores
  "Fetches normalized scores for a list of player IDs.
   Returns a map of {player-id score}."
  [player-ids db]
  (if (empty? player-ids)
    {}
    (let [placeholders (str/join "," (repeat (count player-ids) "?"))
          query (into [(str "SELECT target_id, AVG(stars) AS score FROM Votes WHERE target_id IN (" placeholders ") GROUP BY target_id")]
                      player-ids)
          db-scores (jdbc/execute! db query {:builder-fn rs/as-unqualified-lower-maps})
          scores-map (reduce (fn [acc {s :score id :target_id}]
                               (assoc acc id (double (* 2.0 s))))
                             {}
                             db-scores)]
      (reduce (fn [acc id]
                (if (contains? acc id)
                  acc
                  (assoc acc id 5.0)))
              scores-map
              player-ids))))