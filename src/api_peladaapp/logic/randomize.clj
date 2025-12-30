(ns api-peladaapp.logic.randomize
  (:require [api-peladaapp.db.pelada :as db.pelada]
            [api-peladaapp.db.player :as db.player]
            [api-peladaapp.db.team :as db.team]
            [next.jdbc :as jdbc]))

(defn randomize-teams!
  "Randomly assigns provided players to empty slots in the pelada's teams."
  [pelada-id player-ids players-per-team db-fn]
  (when (and players-per-team (pos? players-per-team) (seq player-ids))
    (jdbc/with-transaction [tx (db-fn)]
      (let [tx-fn (constantly tx)
            pelada (db.pelada/get-pelada pelada-id tx-fn)
            org-id (:organization_id pelada)
            org-player-ids (->> player-ids
                                (map #(db.player/get-org-player-by-user-id % org-id tx-fn))
                                (map :id)
                                (remove nil?))
            teams (db.team/list-pelada-teams pelada-id tx-fn)
            ;; Calculate open slots for each team
            team-slots (reduce (fn [acc team]
                                 (let [current-players (db.team/list-team-players (:id team) tx-fn)
                                       cnt (count current-players)
                                       needed (max 0 (- players-per-team cnt))]
                                   (concat acc (repeat needed (:id team)))))
                               []
                               teams)
            ;; Shuffle players
            shuffled-players (shuffle org-player-ids)
            ;; Pair players with slots
            moves (map vector shuffled-players team-slots)]
        ;; Execute moves
        (doseq [[player-id team-id] moves]
          (when (and player-id team-id)
            (db.team/add-player-to-team team-id player-id tx-fn)))))))