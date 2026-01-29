(ns api-peladaapp.logic.score
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(defn get-normalized-scores
  "Fetches normalized scores for a list of player IDs.
   Returns a map of {player-id score}.
   Uses votes average (* 2) if available, otherwise falls back to player's base grade."
  [player-ids db]
  (if (empty? player-ids)
    {}
    (let [placeholders (str/join "," (repeat (count player-ids) "?"))
          query (into [(str "SELECT op.id, op.grade, AVG(v.stars) AS avg_stars
                             FROM OrganizationPlayers op
                             LEFT JOIN Votes v ON op.id = v.target_id
                             WHERE op.id IN (" placeholders ")
                             GROUP BY op.id")]
                      player-ids)
          db-results (jdbc/execute! db query {:builder-fn rs/as-unqualified-lower-maps})]
      (reduce (fn [acc {:keys [id grade avg_stars]}]
                (let [final-score (if avg_stars
                                    (double (* 2.0 avg_stars))
                                    (double (or grade 5.0)))]
                  (assoc acc id final-score)))
              {}
              db-results))))