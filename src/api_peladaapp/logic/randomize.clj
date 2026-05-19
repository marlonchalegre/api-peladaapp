(ns api-peladaapp.logic.randomize
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [next.jdbc :as jdbc]))

(def position-priority
  {"Goalkeeper" 0
   "Defender" 1
   "Midfielder" 1
   "Striker" 1
   nil 1
   "" 1})

(defn- sort-players-for-balance
  [players num-teams]
  ;; 1. Prioritize Goalkeepers to ensure they are picked to play first.
  ;; 2. Then prioritize by Grade (descending) to get the best field players.
  ;; 3. Finally by position as a tie-breaker.
  (let [sorted (sort-by (juxt #(get position-priority (:position %) 1)
                              (comp - #(or (:grade %) 0))
                              #(or (:position %) ""))
                        players)]
    (if (pos? num-teams)
      (->> sorted
           (partition-all num-teams)
           (mapcat shuffle)
           (vec))
      (vec sorted))))

(defn- get-team-states
  "Calculates current score, player count and position distribution for each team."
  [teams players-per-team org-id tx]
  (mapv (fn [team]
          (let [current-players (db.team/list-team-players (:id team) tx)
                ;; We need grades and positions for current players
                details (if (seq current-players)
                          (db.player/get-players-details-for-balance (map :player-id current-players) org-id tx)
                          [])
                current-grades (->> details (map :grade) (remove nil?) (reduce + 0))
                pos-counts (frequencies (map :position details))]
            {:id (:id team)
             :current-score current-grades
             :current-count (count (filter #(not (:is-goalkeeper %)) current-players))
             :max-players players-per-team
             :positions pos-counts}))
        teams))

(defn- assign-player-to-best-team
  [player team-states]
  (let [pos (:position player)
        eligible-teams (filter #(< (:current-count %) (:max-players %)) team-states)]
    (if (empty? eligible-teams)
      [nil team-states] ;; No slots left
      ;; Priority: 
      ;; 1. Team with fewest players of this position
      ;; 2. Team with lowest total score
      ;; Shuffle first to randomize among equals
      (let [best-team (->> (shuffle eligible-teams)
                           (sort-by (juxt #(get (:positions %) pos 0)
                                          :current-score))
                           first)
            updated-team (-> best-team
                             (update :current-score + (or (:grade player) 0))
                             (update :current-count inc)
                             (update-in [:positions pos] (fnil inc 0)))
            updated-states (mapv #(if (= (:id %) (:id best-team)) updated-team %) team-states)]
        [(:id best-team) updated-states]))))

(defn- calculate-imbalance
  "Calculates the score variance (imbalance) between teams."
  [team-states]
  (let [scores (map :current-score team-states)
        avg (if (seq scores) (/ (double (reduce + scores)) (count scores)) 0)]
    (reduce + (map #(Math/abs (- % avg)) scores))))

(defn- generate-assignment-candidate
  [sorted-players initial-team-states]
  (loop [remaining sorted-players
         states initial-team-states
         acc []]
    (if-let [player (first remaining)]
      (let [[team-id new-states] (assign-player-to-best-team player states)]
        (if team-id
          (recur (rest remaining) new-states (conj acc {:team_id team-id :player_id (:id player) :is_goalkeeper false :final-states new-states}))
          (recur (rest remaining) states acc)))
      {:assignments (map #(dissoc % :final-states) acc)
       :imbalance (calculate-imbalance (:final-states (last acc)))})))

(defn randomize-teams!
  "Randomly assigns provided players to empty slots in the pelada's teams, balancing by position and score.
   Uses a Best-of-N approach to minimize score imbalance."
  [pelada-id player-ids players-per-team db]
  (when (and players-per-team (pos? players-per-team) (seq player-ids))
    (jdbc/with-transaction [tx db]
      (let [pelada (db.pelada/get-pelada pelada-id tx)
            org-id (:organization-id pelada)
            home-gk-id (:home-fixed-goalkeeper-id pelada)
            away-gk-id (:away-fixed-goalkeeper-id pelada)
            global-gk-ids (set (filter some? [home-gk-id away-gk-id]))]

        ;; Clear existing assignments to allow full reshuffle.
        (db.team/clear-teams-players pelada-id tx)

        (let [;; Filter out players who are global fixed goalkeepers
              remaining-player-ids (remove global-gk-ids player-ids)

              ;; Fetch details for players (verifies they belong to org)
              players-details (db.player/get-players-details-for-balance remaining-player-ids org-id tx)

              teams (db.team/list-pelada-teams pelada-id tx)
              num-teams (count teams)

              ;; Initial team states
              initial-team-states (get-team-states teams players-per-team org-id tx)

              ;; Run multiple trials and pick one randomly among those with the lowest imbalance
              candidates (repeatedly 100 (fn []
                                           (let [jittered (map #(update % :grade + (* 0.1 (- (rand) 0.5))) players-details)
                                                 sorted (sort-players-for-balance (shuffle jittered) num-teams)]
                                             (generate-assignment-candidate sorted initial-team-states))))
              sorted-candidates (->> candidates
                                     (group-by :assignments)
                                     vals
                                     (map first)
                                     (sort-by :imbalance))
              min-imb (:imbalance (first sorted-candidates))
              ;; Pick randomly from unique candidates that are "good enough" (within 0.5 of min)
              best-pool (->> sorted-candidates
                             (filter #(<= (:imbalance %) (+ min-imb 0.5)))
                             (take 10))
              best-candidate (rand-nth best-pool)]
          (when (seq (:assignments best-candidate))
            (db.team/add-team-players-batch! (:assignments best-candidate) tx)))))))
