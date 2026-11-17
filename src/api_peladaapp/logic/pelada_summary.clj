(ns api-peladaapp.logic.pelada-summary
  "Pure derivations for a closed pelada: team standings (used for the night's
   champion / final position) and the individual awards (MVP, top scorer and
   assist leader). Mirrors the analysis scripts in the pelada-stats project so
   the app can surface real history instead of placeholder data. Keys follow the
   DB/JSON snake_case convention used by the stats endpoints.")

(defn- empty-standing [team]
  {:team_id (:id team)
   :team_name (:name team)
   :points 0
   :wins 0
   :draws 0
   :losses 0
   :goals_for 0
   :goals_against 0
   :goal_diff 0
   :games 0})

(defn- apply-result [standings match]
  (let [home-id (or (:home_team_id match) (:home-team-id match))
        away-id (or (:away_team_id match) (:away-team-id match))]
    (if-not (and (contains? standings home-id) (contains? standings away-id))
      standings
      (let [hs (int (or (:home_score match) (:home-score match) 0))
            as (int (or (:away_score match) (:away-score match) 0))]
        (-> standings
            (update-in [home-id :games] inc)
            (update-in [away-id :games] inc)
            (update-in [home-id :goals_for] + hs)
            (update-in [home-id :goals_against] + as)
            (update-in [away-id :goals_for] + as)
            (update-in [away-id :goals_against] + hs)
            (cond-> (> hs as) (-> (update-in [home-id :points] + 3)
                                  (update-in [home-id :wins] inc)
                                  (update-in [away-id :losses] inc))
                    (< hs as) (-> (update-in [away-id :points] + 3)
                                  (update-in [away-id :wins] inc)
                                  (update-in [home-id :losses] inc))
                    (= hs as) (-> (update-in [home-id :points] + 1)
                                  (update-in [away-id :points] + 1)
                                  (update-in [home-id :draws] inc)
                                  (update-in [away-id :draws] inc))))))))

(defn- rank-key [{:keys [points wins goal_diff goals_for games]}]
  ;; Teams that never played always rank after teams that played, even when
  ;; both sit on zero points (otherwise a bystander would outrank a loser).
  [(if (pos? games) 0 1) (- points) (- wins) (- goal_diff) (- goals_for)])

(defn- group-or-regular-stage? [match]
  (let [stage (or (:stage match) "regular")]
    (contains? #{"regular" "group"} stage)))

(defn- final-stage? [match]
  (= "final" (:stage match)))

(defn resolve-match-winner-and-loser
  "Given a finished match (in either kebab-case or snake_case), returns [winner-team-id loser-team-id].
   If tied, uses winner-team-id/winner_team_id if present, otherwise home-team-id (higher seed from group stage)."
  [match]
  (let [home-id (or (:home-team-id match) (:home_team_id match))
        away-id (or (:away-team-id match) (:away_team_id match))
        hs (int (or (:home-score match) (:home_score match) 0))
        as (int (or (:away-score match) (:away_score match) 0))
        override-winner (or (:winner-team-id match) (:winner_team_id match))
        winner-id (cond
                    (> hs as) home-id
                    (< hs as) away-id
                    override-winner override-winner
                    :else home-id)
        loser-id (if (= winner-id home-id) away-id home-id)]
    [winner-id loser-id]))

(defn team-standings
  "Given the finished `matches` of a set of peladas and the `teams` of those
   peladas, returns `{pelada-id [standing ...]}` where each standing is ordered
   by final position (1 = champion). Teams without any finished match keep a
   zeroed standing and are ranked after teams that played.
   For championship peladas with knockout matches (`semifinal_1`, `semifinal_2`, `third_place`, `final`),
   only group/regular matches are counted in the points table, and if a finished `final` match exists,
   its winner is placed at position 1 (champion) and runner-up at position 2."
  [matches teams]
  (let [pelada-id-fn #(or (:pelada_id %) (:pelada-id %))
        teams-by-pelada (group-by pelada-id-fn teams)
        base (into {}
                   (for [[pelada-id pelada-teams] teams-by-pelada]
                     [pelada-id (into {} (map (juxt :id empty-standing) pelada-teams))]))
        table-matches (filter group-or-regular-stage? matches)
        finals-by-pelada (into {}
                               (for [m matches
                                     :when (final-stage? m)]
                                 [(pelada-id-fn m) m]))
        applied (reduce
                 (fn [acc match]
                   (let [pid (pelada-id-fn match)]
                     (if-let [standings (get acc pid)]
                       (assoc acc pid (apply-result standings match))
                       acc)))
                 base
                 table-matches)]
    (into {}
          (for [[pelada-id standings] applied]
            (let [final-match (get finals-by-pelada pelada-id)
                  [final-winner-id final-loser-id] (when final-match (resolve-match-winner-and-loser final-match))
                  standings-with-final (if final-match
                                         (-> standings
                                             (update-in [final-winner-id :games] (fnil max 0) 1)
                                             (update-in [final-loser-id :games] (fnil max 0) 1))
                                         standings)]
              [pelada-id (->> standings-with-final
                              vals
                              (map (fn [s] (assoc s :goal_diff (- (:goals_for s) (:goals_against s)))))
                              (sort-by (fn [s]
                                         [(cond
                                            (and final-winner-id (= (:team_id s) final-winner-id)) -2
                                            (and final-loser-id (= (:team_id s) final-loser-id)) -1
                                            :else 0)
                                          (rank-key s)]))
                              (map-indexed (fn [idx s] (assoc s :position (inc idx)))))])))))

(defn champion
  "Returns the champion standing of a pelada, or nil when no match was played."
  [standings]
  (when-let [first-standing (first standings)]
    (when (pos? (:games first-standing))
      first-standing)))

(defn- best-by
  "Returns the participant with the highest `k` (descending), breaking ties by
   vote count. Nil values for `k` are treated as zero."
  [participants k]
  (->> participants
       (filter #(pos? (int (or (get % k) 0))))
       (sort-by (juxt #(- (int (or (get % k) 0)))
                      #(- (int (or (:vote_count %) 0)))))
       first))

(defn awards
  "Given the participant lines of a pelada, returns `{:mvp :top-scorer :garcom}`
   where each entry is the winning participant map (or nil)."
  [participants]
  {:mvp (->> participants
             (filter #(pos? (int (or (:vote_count %) 0))))
             (sort-by (juxt #(- (double (or (:avg_stars %) 0.0)))
                            #(- (int (or (:vote_count %) 0)))))
             first)
   :top-scorer (best-by participants :goals)
   :garcom (best-by participants :assists)})
