(ns api-peladaapp.logic.randomize
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.team :as db.team]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(def position-priority
  {"Goalkeeper" 0
   "Defender" 1
   "Midfielder" 2
   "Striker" 3
   nil 4
   "" 4})

(defn- get-player-details
  "Fetches grade and position for the given player-ids within the organization."
  [player-ids org-id db]
  (if (empty? player-ids)
    []
    (jdbc/execute! db
                   (into [(str "SELECT op.id as id, op.grade as grade, u.position as position
                                FROM OrganizationPlayers op
                                JOIN Users u ON op.user_id = u.id
                                WHERE op.organization_id = ? AND op.id IN (" (str/join "," (repeat (count player-ids) "?")) ")")
                          org-id]
                         player-ids)
                   {:builder-fn rs/as-unqualified-lower-maps})))

(defn- sort-players-for-balance
  [players]
  (sort-by (juxt #(get position-priority (:position %) 4)
                 (comp - #(or (:grade %) 0)))
           players))

(defn- get-team-states
  "Calculates current score, player count and position distribution for each team."
  [teams players-per-team org-id tx]
  (mapv (fn [team]
          (let [current-players (db.team/list-team-players (:id team) tx)
                ;; We need grades and positions for current players
                details (if (seq current-players)
                          (get-player-details (map :player-id current-players) org-id tx)
                          [])
                current-grades (->> details (map :grade) (remove nil?) (reduce + 0))
                pos-counts (frequencies (map :position details))]
            {:id (:id team)
             :current-score current-grades
             :current-count (count current-players)
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
      ;; Clear existing assignments to allow full reshuffle
      (db.team/clear-teams-players pelada-id tx)
      
      (let [pelada (db.pelada/get-pelada pelada-id tx)
            org-id (:organization-id pelada)
            
            ;; Fetch details for players (verifies they belong to org)
            ;; We assume player-ids are OrganizationPlayer IDs (not User IDs)
            players-details (get-player-details player-ids org-id tx)
            
            ;; Sort players: Position first, then Grade
            ;; Shuffle first to ensure random order for players with same position/score
            sorted-players (sort-players-for-balance (shuffle players-details))
            
            teams (db.team/list-pelada-teams pelada-id tx)
            
            ;; Initial team states
            initial-team-states (get-team-states teams players-per-team org-id tx)]

        ;; Distribute players
        (loop [players sorted-players
               states initial-team-states]
          (when-let [player (first players)]
            (let [[team-id new-states] (assign-player-to-best-team player states)]
              (when team-id
                (db.team/add-player-to-team team-id (:id player) tx)
                (recur (rest players) new-states)))))))))