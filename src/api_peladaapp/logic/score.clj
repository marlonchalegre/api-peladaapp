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
                      player-ids)]
      (reduce (fn [acc {s :score id :target_id}]
                (assoc acc id s))
              {}
              (jdbc/execute! db query {:builder-fn rs/as-unqualified-lower-maps})))))