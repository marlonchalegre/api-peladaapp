(ns api-peladaapp.logic.randomize
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [next.jdbc :as jdbc]))

(def position-priority
  {"Goalkeeper" 0
   "Defender" 1
   "Midfielder" 2
   "Striker" 3
   nil 4
   "" 4})

(defn- sort-players-for-balance
  [players num-teams]
  (let [sorted (sort-by (juxt #(get position-priority (:position %) 4)
                              (comp - #(or (:grade %) 0)))
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

(defn randomize-teams!
  "Randomly assigns provided players to empty slots in the pelada's teams, balancing by position and score."
  [pelada-id player-ids players-per-team db]
  (when (and players-per-team (pos? players-per-team) (seq player-ids))
    (jdbc/with-transaction [tx db]
      (let [pelada (db.pelada/get-pelada pelada-id tx)
            org-id (:organization-id pelada)
            home-gk-id (:home-fixed-goalkeeper-id pelada)
            away-gk-id (:away-fixed-goalkeeper-id pelada)
            global-gk-ids (set (filter some? [home-gk-id away-gk-id]))]

        ;; Clear existing assignments to allow full reshuffle.
        ;; Note: In global fixed GK mode, these players are NOT in any team.
        (db.team/clear-teams-players pelada-id tx)

        (let [;; Filter out players who are global fixed goalkeepers
              remaining-player-ids (remove global-gk-ids player-ids)

              ;; Fetch details for players (verifies they belong to org)
              players-details (db.player/get-players-details-for-balance remaining-player-ids org-id tx)

              teams (db.team/list-pelada-teams pelada-id tx)
              num-teams (count teams)

              ;; Sort players: Position first, then Grade, with bucket shuffle for variety
              sorted-players (sort-players-for-balance (shuffle players-details) num-teams)

              ;; Initial team states
              initial-team-states (get-team-states teams players-per-team org-id tx)

              ;; Distribute remaining players and collect assignments for batch insert
              assignments (loop [remaining sorted-players
                                 states initial-team-states
                                 acc []]
                            (if-let [player (first remaining)]
                              (let [[team-id new-states] (assign-player-to-best-team player states)]
                                (if team-id
                                  (recur (rest remaining) new-states (conj acc {:team_id team-id :player_id (:id player) :is_goalkeeper false}))
                                  (recur (rest remaining) states acc)))
                              acc))]
          (when (seq assignments)
            (db.team/add-team-players-batch! assignments tx)))))))
