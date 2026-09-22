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

(defn- apply-result [standings {:keys [home_team_id away_team_id home_score away_score]}]
  (if-not (and (contains? standings home_team_id) (contains? standings away_team_id))
    standings
    (let [hs (int (or home_score 0))
          as (int (or away_score 0))]
      (-> standings
          (update-in [home_team_id :games] inc)
          (update-in [away_team_id :games] inc)
          (update-in [home_team_id :goals_for] + hs)
          (update-in [home_team_id :goals_against] + as)
          (update-in [away_team_id :goals_for] + as)
          (update-in [away_team_id :goals_against] + hs)
          (cond-> (> hs as) (-> (update-in [home_team_id :points] + 3)
                                (update-in [home_team_id :wins] inc)
                                (update-in [away_team_id :losses] inc))
                  (< hs as) (-> (update-in [away_team_id :points] + 3)
                                (update-in [away_team_id :wins] inc)
                                (update-in [home_team_id :losses] inc))
                  (= hs as) (-> (update-in [home_team_id :points] + 1)
                                (update-in [away_team_id :points] + 1)
                                (update-in [home_team_id :draws] inc)
                                (update-in [away_team_id :draws] inc)))))))

(defn- rank-key [{:keys [points wins goal_diff goals_for games]}]
  ;; Teams that never played always rank after teams that played, even when
  ;; both sit on zero points (otherwise a bystander would outrank a loser).
  [(if (pos? games) 0 1) (- points) (- wins) (- goal_diff) (- goals_for)])

(defn team-standings
  "Given the finished `matches` of a set of peladas and the `teams` of those
   peladas, returns `{pelada-id [standing ...]}` where each standing is ordered
   by final position (1 = champion). Teams without any finished match keep a
   zeroed standing and are ranked after teams that played."
  [matches teams]
  (let [teams-by-pelada (group-by :pelada_id teams)
        base (into {}
                   (for [[pelada-id pelada-teams] teams-by-pelada]
                     [pelada-id (into {} (map (juxt :id empty-standing) pelada-teams))]))
        applied (reduce
                 (fn [acc match]
                   (if-let [standings (get acc (:pelada_id match))]
                     (assoc acc (:pelada_id match) (apply-result standings match))
                     acc))
                 base
                 matches)]
    (into {}
          (for [[pelada-id standings] applied]
            [pelada-id (->> standings
                            vals
                            (map (fn [s] (assoc s :goal_diff (- (:goals_for s) (:goals_against s)))))
                            (sort-by rank-key)
                            (map-indexed (fn [idx s] (assoc s :position (inc idx)))))]))))

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
