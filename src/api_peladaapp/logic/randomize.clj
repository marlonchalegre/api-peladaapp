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
  "Calculates current score and player count for each team."
  [teams players-per-team org-id tx]
  (mapv (fn [team]
          (let [current-players (db.team/list-team-players-by-pelada (:id team) tx)
                ;; We need grades for current players to calculate score
                current-grades (if (seq current-players)
                                 (->> (get-player-details (map :player_id current-players) org-id tx)
                                      (map :grade)
                                      (remove nil?)
                                      (reduce + 0))
                                 0)]
            {:id (:id team)
             :current-score current-grades
             :current-count (count current-players)
             :max-players players-per-team}))
        teams))

(defn- assign-player-to-best-team
  [player team-states]
  (let [eligible-teams (filter #(< (:current-count %) (:max-players %)) team-states)]
    (if (empty? eligible-teams)
      [nil team-states] ;; No slots left
      (let [best-team (apply min-key :current-score eligible-teams)
            updated-team (-> best-team
                             (update :current-score + (or (:grade player) 0))
                             (update :current-count inc))
            updated-states (mapv #(if (= (:id %) (:id best-team)) updated-team %) team-states)]
        [(:id best-team) updated-states]))))

(defn randomize-teams!
  "Randomly assigns provided players to empty slots in the pelada's teams, balancing by position and score."
  [pelada-id player-ids players-per-team db]
  (when (and players-per-team (pos? players-per-team) (seq player-ids))
    (jdbc/with-transaction [tx db]
      (let [pelada (db.pelada/get-pelada pelada-id tx)
            org-id (:organization-id pelada)
            
            ;; Fetch details for players (verifies they belong to org)
            ;; We assume player-ids are OrganizationPlayer IDs (not User IDs)
            players-details (get-player-details player-ids org-id tx)
            
            ;; Sort players: Position first, then Grade
            sorted-players (sort-players-for-balance players-details)
            
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