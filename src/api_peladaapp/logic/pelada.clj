(ns api-peladaapp.logic.pelada
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.pelada-summary :as pelada-summary]
   [api-peladaapp.logic.schedule :as schedule]
   [api-peladaapp.logic.vote :as logic.vote]
   [schema.core :as s]))

(defn ensure-open
  "Ensure pelada can start. Returns pelada or throws with :bad-request."
  [pelada]
  (case (:status pelada)
    "open" pelada
    "attendance" (throw (ex-info "Attendance list is still open. Close it before starting the pelada." {:type :bad-request
                                                                                                        :message "Attendance list is still open. Close it before starting the pelada."}))
    (throw (ex-info "Pelada already started or closed" {:type :bad-request
                                                        :message "Pelada already started or closed"}))))

(defn ensure-running
  "Ensure pelada is currently running. Returns pelada or throws with :bad-request.
   Optional :allow-closed? allows closed/voting status."
  ([pelada] (ensure-running pelada {}))
  ([pelada opts]
   (let [status (:status pelada)
         allowed? (or (= "running" status)
                      (and (:allow-closed? opts)
                           (contains? #{"closed" "voting"} status)))]
     (if allowed?
       pelada
       (throw (ex-info "Pelada is not running"
                       {:type :bad-request
                        :message (str "Action only allowed while pelada is running. Current status: " status)}))))))

(defn ensure-schedulable-team-count
  "Ensure there are enough teams for the selected pelada format. Returns team ids."
  ([team-ids] (ensure-schedulable-team-count team-ids "round_robin"))
  ([team-ids pelada-format]
   (let [team-count (count team-ids)
         fmt (or pelada-format "round_robin")]
     (cond
       (< team-count 2)
       (throw (ex-info "At least two teams are required" {:type :bad-request
                                                          :message "At least two teams are required"}))

       (and (= fmt "championship_final") (< team-count 3))
       (throw (ex-info "At least three teams are required for Group Stage + Final"
                       {:type :bad-request
                        :message "At least three teams are required for Group Stage + Final"}))

       (and (= fmt "championship_semis_final") (< team-count 4))
       (throw (ex-info "At least four teams are required for Group Stage + Semifinals & Final"
                       {:type :bad-request
                        :message "At least four teams are required for Group Stage + Semifinals & Final"}))

       :else (vec team-ids)))))

(s/defn get-voting-info :- s/Any
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db) ;; Re-fetch pelada to ensure latest status and closed_at
        can-vote (try (logic.vote/validate-voting-eligibility pelada) true (catch Exception _ false))
        has-voted (db.vote/has-voter-voted? pelada-id player-id db)]
    {:can-vote can-vote
     :has-voted has-voted
     :eligible-players [] ;; In this simplified context we don't need players
     :message (if (not can-vote)
                "Voting is not open or has closed."
                "")}))

(defn ensure-startable
  "Validate pelada start preconditions. Returns vector of team ids."
  [pelada team-ids]
  (ensure-open pelada)
  (ensure-schedulable-team-count team-ids (:format pelada)))

(defn default-stage-for-format
  "Returns the initial match stage string for a given pelada format."
  [pelada-format]
  (if (contains? #{"championship_final" "championship_semis_final"} pelada-format)
    "group"
    "regular"))

(defn schedule-matches-for-start
  "Return sequence of match maps {:home :away :stage} honoring optional matches-per-team and format."
  ([team-ids matches-per-team]
   (schedule-matches-for-start team-ids matches-per-team "round_robin"))
  ([team-ids matches-per-team pelada-format]
   (let [ids (vec team-ids)
         fmt (or pelada-format "round_robin")
         stage (default-stage-for-format fmt)]
     (if (= fmt "winner_stays_on")
       (if (>= (count ids) 2)
         [{:home (nth ids 0) :away (nth ids 1) :stage "regular"}]
         [])
       (let [raw (if matches-per-team
                   (schedule/schedule-matches-with-limit ids matches-per-team)
                   (schedule/schedule-matches ids))]
         (mapv #(assoc % :stage stage) raw))))))

(defn match-plan->rows
  "Convert scheduled matches into DB ready rows."
  [pelada-id scheduled-matches]
  (map-indexed (fn [index {:keys [home away stage]}]
                 {:pelada-id pelada-id
                  :home-team-id home
                  :away-team-id away
                  :sequence (inc index)
                  :status "scheduled"
                  :home-score 0
                  :away-score 0
                  :stage (or stage "regular")})
               scheduled-matches))

(def resolve-knockout-winner-and-loser
  pelada-summary/resolve-match-winner-and-loser)

(defn- advance-staying-team
  [staying-id leaving-id clean-queue streak max-wins]
  (let [wins (inc (get streak staying-id 0))]
    (if (and (pos? max-wins) (>= wins max-wins) (>= (count clean-queue) 2))
      {:next-home (nth clean-queue 0)
       :next-away (nth clean-queue 1)
       :queue (-> (subvec clean-queue 2) (conj leaving-id) (conj staying-id))
       :streak {}}
      (if (empty? clean-queue)
        {:next-home staying-id
         :next-away leaving-id
         :queue []
         :streak {staying-id wins}}
        {:next-home staying-id
         :next-away (first clean-queue)
         :queue (-> (vec (rest clean-queue)) (conj leaving-id))
         :streak {staying-id wins}}))))

(defn compute-winner-stays-on-next-match
  "Replays finished matches in sequence order for `winner_stays_on` and returns
   the next match map `{:home <team-id> :away <team-id> :stage \"regular\"}`."
  [team-ids finished-matches format-config]
  (let [ids (vec team-ids)
        draw-rule (or (:draw_rule format-config) (:draw-rule format-config) "challenger_stays")
        max-wins (int (or (:max_consecutive_wins format-config) (:max-consecutive-wins format-config) 0))]
    (if (< (count ids) 2)
      nil
      (let [ordered-matches (sort-by :sequence finished-matches)]
        (if (empty? ordered-matches)
          {:home (nth ids 0) :away (nth ids 1) :stage "regular"}
          (let [initial-queue (vec (drop 2 ids))
                final-state
                (reduce
                 (fn [{:keys [queue streak]} m]
                   (let [home-id (or (:home-team-id m) (:home_team_id m))
                         away-id (or (:away-team-id m) (:away_team_id m))
                         hs (int (or (:home-score m) (:home_score m) 0))
                         as (int (or (:away-score m) (:away_score m) 0))
                         clean-queue (->> (concat queue ids)
                                          distinct
                                          (remove #{home-id away-id})
                                          vec)]
                     (cond
                       (> hs as)
                       (advance-staying-team home-id away-id clean-queue streak max-wins)

                       (< hs as)
                       (advance-staying-team away-id home-id clean-queue streak max-wins)

                       :else
                       (case draw-rule
                         "both_leave"
                         (cond
                           (>= (count clean-queue) 2)
                           {:next-home (nth clean-queue 0)
                            :next-away (nth clean-queue 1)
                            :queue (-> (subvec clean-queue 2) (conj home-id) (conj away-id))
                            :streak {}}

                           (= (count clean-queue) 1)
                           {:next-home (first clean-queue)
                            :next-away away-id
                            :queue [home-id]
                            :streak {}}

                           :else
                           {:next-home away-id
                            :next-away home-id
                            :queue []
                            :streak {}})

                         "defender_stays"
                         (advance-staying-team home-id away-id clean-queue streak max-wins)

                         ;; Default: "challenger_stays" (Away stays, Home/oldest leaves)
                         (advance-staying-team away-id home-id clean-queue streak max-wins)))))
                 {:queue initial-queue :streak {}}
                 ordered-matches)]
            (when (and (:next-home final-state) (:next-away final-state))
              {:home (:next-home final-state)
               :away (:next-away final-state)
               :stage "regular"})))))))

(defn compute-next-progression-matches
  "Pure state-machine calculation returning a vector of new match specs
   `[{:home <uuid> :away <uuid> :stage <str>}]` to create when a match finishes."
  [pelada all-matches teams]
  (let [pelada-id (:id pelada)
        fmt (or (:format pelada) "round_robin")
        fmt-config (or (:format-config pelada) {})
        group-matches (filter #(contains? #{"regular" "group"} (or (:stage %) "regular")) all-matches)
        all-group-finished? (and (seq group-matches)
                                 (every? #(= "finished" (:status %)) group-matches))
        teams-with-pelada (map #(assoc % :pelada-id pelada-id) teams)]
    (case fmt
      "championship_final"
      (let [final-exists? (some #(= "final" (:stage %)) all-matches)]
        (if (and all-group-finished? (not final-exists?) (>= (count teams) 2))
          (let [standings (get (pelada-summary/team-standings group-matches teams-with-pelada) pelada-id)
                first-team (:team_id (nth standings 0 nil))
                second-team (:team_id (nth standings 1 nil))]
            (if (and first-team second-team (not= first-team second-team))
              [{:home first-team :away second-team :stage "final"}]
              []))
          []))

      "championship_semis_final"
      (let [sf1 (first (filter #(= "semifinal_1" (:stage %)) all-matches))
            sf2 (first (filter #(= "semifinal_2" (:stage %)) all-matches))
            final-exists? (some #(= "final" (:stage %)) all-matches)]
        (cond
          (and all-group-finished? (nil? sf1) (nil? sf2) (>= (count teams) 4))
          (let [standings (get (pelada-summary/team-standings group-matches teams-with-pelada) pelada-id)
                t1 (:team_id (nth standings 0 nil))
                t2 (:team_id (nth standings 1 nil))
                t3 (:team_id (nth standings 2 nil))
                t4 (:team_id (nth standings 3 nil))]
            (if (and t1 t2 t3 t4)
              [{:home t1 :away t4 :stage "semifinal_1"}
               {:home t2 :away t3 :stage "semifinal_2"}]
              []))

          (and sf1 sf2
               (= "finished" (:status sf1))
               (= "finished" (:status sf2))
               (not final-exists?))
          (let [[w1 l1] (resolve-knockout-winner-and-loser sf1)
                [w2 l2] (resolve-knockout-winner-and-loser sf2)
                include-3rd? (boolean (or (:include_third_place fmt-config) (:include-third-place fmt-config)))]
            (cond-> []
              (and include-3rd? l1 l2 (not= l1 l2))
              (conj {:home l1 :away l2 :stage "third_place"})
              (and w1 w2 (not= w1 w2))
              (conj {:home w1 :away w2 :stage "final"})))

          :else []))

      "winner_stays_on"
      (let [has-unfinished? (some #(not= "finished" (:status %)) all-matches)
            finished-matches (filter #(= "finished" (:status %)) all-matches)]
        (if (and (not has-unfinished?) (seq finished-matches) (>= (count teams) 2))
          (if-let [next-spec (compute-winner-stays-on-next-match
                              (mapv :id teams)
                              finished-matches
                              fmt-config)]
            [next-spec]
            [])
          []))

      [])))

(defn- build-match-lineup-lookup
  [finished-matches match-lineups team-roster-map fixed-gk-ids]
  (let [by-match-team (reduce (fn [acc row]
                                (let [mid (or (:match-id row) (:match_id row))
                                      tid (or (:team-id row) (:team_id row))
                                      pid (or (:player-id row) (:player_id row))]
                                  (if (and mid tid pid (not (fixed-gk-ids pid)))
                                    (update-in acc [mid tid] (fnil conj []) pid)
                                    acc)))
                              {}
                              match-lineups)]
    (into {}
          (map (fn [m]
                 (let [mid (:id m)
                       hid (or (:home-team-id m) (:home_team_id m))
                       aid (or (:away-team-id m) (:away_team_id m))
                       explicit (get by-match-team mid)]
                   [mid {hid (vec (distinct (or (get explicit hid) (get team-roster-map hid) [])))
                         aid (vec (distinct (or (get explicit aid) (get team-roster-map aid) [])))}]))
               finished-matches))))

(defn- compute-player-play-stats
  [ordered-matches lineup-lookup]
  (let [rev-matches (reverse ordered-matches)]
    (first
     (reduce
      (fn [[acc streak-active?] m]
        (let [seq-num (or (:sequence m) 0)
              sides (get lineup-lookup (:id m) {})
              played-set (into #{} (apply concat (vals sides)))
              next-acc (reduce (fn [a pid]
                                 (let [cur (get a pid {:total-games 0 :consecutive-games 0 :last-played-seq 0})
                                       can-streak? (contains? streak-active? pid)]
                                   (assoc a pid {:total-games (inc (:total-games cur))
                                                 :consecutive-games (if (or (empty? a) can-streak?)
                                                                      (inc (:consecutive-games cur))
                                                                      (:consecutive-games cur))
                                                 :last-played-seq (max (:last-played-seq cur) seq-num)})))
                               acc
                               played-set)]
          [next-acc (into #{} (filter streak-active? played-set))]))
      [{} (into #{} (apply concat (vals (get lineup-lookup (:id (first rev-matches)) {}))))]
      rev-matches))))

(defn- sort-leaving-candidates
  [candidates player-stats arrival-rank-map player-info-map staying-avg-grade complete-rule]
  (let [stats-fn (fn [pid] (get player-stats pid {:consecutive-games 0 :total-games 0 :last-played-seq 0}))
        rank-fn (fn [pid] (get arrival-rank-map pid 999999))]
    (case complete-rule
      "random"
      (->> candidates
           (group-by #(:consecutive-games (stats-fn %)))
           (sort-by key)
           (mapcat (fn [[_ grp]] (shuffle grp)))
           vec)

      "balanced"
      (sort-by (fn [pid]
                 (let [{:keys [consecutive-games total-games]} (stats-fn pid)
                       grade (double (or (:grade (get player-info-map pid)) 5.0))]
                   [consecutive-games (Math/abs (- grade staying-avg-grade)) total-games (rank-fn pid)]))
               candidates)

      ;; Default: "least_played"
      (sort-by (fn [pid]
                 (let [{:keys [consecutive-games total-games]} (stats-fn pid)]
                   [consecutive-games total-games (rank-fn pid)]))
               candidates))))

(defn- build-wso-context
  [pelada staying-id away-id finished-matches match-lineups team-players confirmed-players]
  (let [fmt-config (or (:format-config pelada) (:format_config pelada) {})
        rotation-mode (or (:rotation_mode fmt-config) (:rotation-mode fmt-config) "team_queue")
        complete-rule (or (:complete_team_rule fmt-config) (:complete-team-rule fmt-config) "least_played")
        players-per-team (int (or (:players-per-team pelada) (:players_per_team pelada) 5))
        fixed-gks? (boolean (or (:fixed-goalkeepers pelada) (:fixed_goalkeepers pelada)))
        fixed-gk-ids (if fixed-gks?
                       (into #{} (remove nil? [(or (:home-fixed-goalkeeper-id pelada) (:home_fixed_goalkeeper_id pelada))
                                               (or (:away-fixed-goalkeeper-id pelada) (:away_fixed_goalkeeper_id pelada))]))
                       #{})
        team-roster-map (reduce (fn [acc row]
                                  (let [tid (or (:team-id row) (:team_id row))
                                        pid (or (:player-id row) (:player_id row))]
                                    (if (and tid pid (not (fixed-gk-ids pid)))
                                      (update acc tid (fnil conj []) pid)
                                      acc)))
                                {}
                                team-players)
        ordered-matches (vec (sort-by :sequence finished-matches))
        last-match (peek ordered-matches)
        last-home (or (:home-team-id last-match) (:home_team_id last-match))
        last-away (or (:away-team-id last-match) (:away_team_id last-match))
        lineup-lookup (build-match-lineup-lookup ordered-matches match-lineups team-roster-map fixed-gk-ids)
        player-stats (compute-player-play-stats ordered-matches lineup-lookup)
        confirmed-ids (->> confirmed-players
                           (map #(or (:player-id %) (:player_id %)))
                           (remove fixed-gk-ids)
                           distinct
                           vec)
        arrival-rank-map (into {} (map-indexed (fn [idx pid] [pid idx]) confirmed-ids))
        player-info-map (into {} (map (fn [p] [(or (:player-id p) (:player_id p)) p]) confirmed-players))
        last-sides (get lineup-lookup (:id last-match) {})
        staying-lineup (->> (if (#{last-home last-away} staying-id)
                              (get last-sides staying-id)
                              (get team-roster-map staying-id))
                            (take players-per-team)
                            vec)
        staying-set (set staying-lineup)
        leaving-id (cond
                     (= staying-id last-home) last-away
                     (= staying-id last-away) last-home
                     :else nil)
        leaving-lineup (->> (if leaving-id (get last-sides leaving-id) [])
                            (remove staying-set)
                            vec)
        last-match-players (into #{} (apply concat (vals last-sides)))
        bench-players (->> confirmed-ids
                           (remove staying-set)
                           (remove last-match-players)
                           (sort-by (fn [pid]
                                      (let [{:keys [last-played-seq total-games]} (get player-stats pid {:last-played-seq 0 :total-games 0})]
                                        [last-played-seq total-games (get arrival-rank-map pid 999999)])))
                           vec)
        team-count (count (keys team-roster-map))
        away-initial (if (or (= rotation-mode "player_queue") (< team-count 3))
                       (vec (take players-per-team bench-players))
                       (vec (take players-per-team (remove staying-set (get team-roster-map away-id)))))]
    {:rotation-mode rotation-mode
     :complete-rule complete-rule
     :players-per-team players-per-team
     :fixed-gk-ids fixed-gk-ids
     :team-roster-map team-roster-map
     :team-count team-count
     :player-stats player-stats
     :arrival-rank-map arrival-rank-map
     :player-info-map player-info-map
     :staying-lineup staying-lineup
     :staying-set staying-set
     :leaving-lineup leaving-lineup
     :bench-players bench-players
     :away-initial away-initial}))

(defn compute-winner-stays-on-lineups
  "Computes dynamic {:home-lineup [...] :away-lineup [...]} for `winner_stays_on`
   when `player_queue` rotation is enabled, a team has fewer than `players-per-team`
   players, or bench players exist with < 3 teams. Returns nil if standard team
   seeding should be used."
  [pelada next-match-spec finished-matches match-lineups team-players confirmed-players]
  (let [fmt (or (:format pelada) "round_robin")]
    (when (and (= fmt "winner_stays_on") next-match-spec (seq finished-matches))
      (let [staying-id (:home next-match-spec)
            away-id (:away next-match-spec)
            {:keys [rotation-mode complete-rule players-per-team fixed-gk-ids
                    team-roster-map team-count player-stats arrival-rank-map
                    player-info-map staying-lineup staying-set leaving-lineup
                    bench-players away-initial]}
            (build-wso-context pelada staying-id away-id finished-matches match-lineups team-players confirmed-players)
            assigned-set (into #{} (mapcat val team-roster-map))
            bench-unassigned? (some #(let [pid (or (:player-id %) (:player_id %))]
                                       (and (not (fixed-gk-ids pid))
                                            (not (assigned-set pid))))
                                    confirmed-players)
            needs-dynamic? (or (= rotation-mode "player_queue")
                               (< (count (get team-roster-map away-id)) players-per-team)
                               (and (< team-count 3) bench-unassigned?))]
        (when needs-dynamic?
          (let [away-initial-set (into staying-set away-initial)
                remaining-bench (vec (remove away-initial-set bench-players))
                staying-grades (keep #(:grade (get player-info-map %)) staying-lineup)
                staying-avg-grade (if (seq staying-grades)
                                    (/ (reduce + 0.0 staying-grades) (count staying-grades))
                                    5.0)
                sorted-leaving (sort-leaving-candidates (remove away-initial-set leaving-lineup)
                                                        player-stats
                                                        arrival-rank-map
                                                        player-info-map
                                                        staying-avg-grade
                                                        complete-rule)
                needed (max 0 (- players-per-team (count away-initial)))
                fillers (take needed (concat remaining-bench sorted-leaving))
                away-lineup (vec (concat away-initial fillers))]
            {:home-lineup staying-lineup
             :away-lineup away-lineup}))))))

(defn get-challenger-completion-context
  "Returns challenger completion context for a `winner_stays_on` match, or nil
   when the pelada is not `winner_stays_on` or no prior finished match exists."
  [pelada target-match finished-matches match-lineups team-players confirmed-players]
  (let [fmt (or (:format pelada) "round_robin")]
    (when (and (= fmt "winner_stays_on") target-match (seq finished-matches))
      (let [staying-id (or (:home-team-id target-match) (:home_team_id target-match))
            away-id (or (:away-team-id target-match) (:away_team_id target-match))
            {:keys [players-per-team fixed-gk-ids player-stats leaving-lineup away-initial]}
            (build-wso-context pelada staying-id away-id finished-matches match-lineups team-players confirmed-players)
            needed (max 0 (- players-per-team (count away-initial)))
            candidate-ids (vec (remove (set away-initial) leaving-lineup))
            sorted-by-consec (sort-by #(:consecutive-games (get player-stats % {:consecutive-games 0})) candidate-ids)
            cutoff-consec (when (and (pos? needed) (seq sorted-by-consec))
                            (let [nth-pid (nth sorted-by-consec (min (dec needed) (dec (count sorted-by-consec))))]
                              (:consecutive-games (get player-stats nth-pid {:consecutive-games 0}))))
            candidates (mapv (fn [pid]
                               (let [{:keys [consecutive-games total-games]} (get player-stats pid {:consecutive-games 0 :total-games 0})]
                                 {:player_id pid
                                  :consecutive_games consecutive-games
                                  :total_games total-games
                                  :eligible_by_fairness (boolean (and cutoff-consec (<= consecutive-games cutoff-consec)))}))
                             sorted-by-consec)
            current-away (->> match-lineups
                              (filter #(and (= (:match_id %) (:id target-match))
                                            (= (:team_id %) away-id)))
                              (map :player_id)
                              (remove fixed-gk-ids)
                              (remove (set away-initial))
                              vec)]
        {:needed needed
         :away_team_id away-id
         :bench_player_ids away-initial
         :selected_player_ids current-away
         :candidates candidates}))))
