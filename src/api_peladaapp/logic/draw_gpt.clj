(ns api-peladaapp.logic.draw-gpt
  "Constraint-first draw: 'Equilíbrio Tático'.

   Ported from the reference generator in pelada-stats/analise/divisao_*.py and
   generalised from its fixed 4x5 roster to any number of teams and squad size.

   The order of decisions is what characterises this algorithm — it is
   lexicographic, not a weighted sum, so a later criterion can never buy its
   way past an earlier one:

     1. one reference player (highest grades) per team;
     2. the gap between team averages stays inside an explicit tolerance;
     3. spread out the players with the fewest recorded nights;
     4. level the strongest sector, adjusted by the history penalty;
     5. the overall gap breaks remaining ties.

   The search is deterministic: same squad and same options give the same
   division every time."
  (:require
   [api-peladaapp.logic.draw-common :as common]
   [api-peladaapp.logic.draw-history :as history]))

(def algorithm-name "gpt")

(def default-tolerance
  "Maximum accepted difference between the highest and lowest team average."
  0.10)

(def short-history-threshold
  "Below this many recorded nights a player is treated as little-observed."
  5)

(def ^:private restarts 12)
(def ^:private max-climb-steps 400)

(defn- main-sector
  "The sector the draw levels after the hard constraints. Midfield when the
   squad has one, otherwise the most numerous sector."
  [players]
  (let [counts (frequencies (map #(common/normalize-position (:position %)) players))]
    (if (contains? counts common/midfielder)
      common/midfielder
      (->> counts (sort-by (juxt (comp - val) key)) ffirst))))

(defn- anchors
  "The `n` highest graded players. One per team keeps the strongest names from
   stacking up, which is the rule the group already used by hand."
  [players n]
  (->> players
       (sort-by (juxt #(- (double (or (:grade %) 0.0))) :name))
       (take n)
       (map :id)
       set))

(defn- sector-mean
  [team sector]
  (let [members (common/sector-players team sector)]
    (when (seq members)
      (common/team-mean members :grade))))

(defn- cost-fn
  "Lexicographic cost vector. Lower compares better, element by element."
  [{:keys [anchor-ids short-ids sector tolerance history-weight score-history]}]
  (fn [teams]
    (let [means (map #(common/team-mean % :grade) teams)
          overall-gap (common/spread means)
          anchor-violations (reduce + 0 (map (fn [team]
                                               (let [n (count (filter #(anchor-ids (:id %)) team))]
                                                 (Math/abs (- n 1))))
                                             teams))
          tolerance-excess (max 0.0 (- overall-gap tolerance))
          concentration (apply max 0 (map (fn [team] (count (filter #(short-ids (:id %)) team))) teams))
          sector-means (keep #(sector-mean % sector) teams)
          sector-gap (common/spread sector-means)
          penalty (if (and score-history (pos? history-weight))
                    (:penalty (score-history (map #(map :id %) teams)))
                    0.0)]
      [(double anchor-violations)
       tolerance-excess
       (double concentration)
       (+ sector-gap (* history-weight penalty))
       overall-gap])))

(defn- better?
  [a b]
  (neg? (compare a b)))

(defn- search
  [players num-teams context]
  (let [cost (cost-fn context)]
    (->> (range restarts)
         (map (fn [seed]
                (let [rng (common/seeded-random (+ 7919 (* 131 seed)))
                      start (common/balanced-distribution players num-teams rng)]
                  (common/hill-climb start cost better? max-climb-steps))))
         (sort-by second compare)
         first)))

(defn- pair-evidence
  [history-signals team-ids]
  (when history-signals
    (let [features (:pairs (history/team-features history-signals team-ids))]
      {:top_pairs (->> features
                       (filter #(pos? (:nights_together %)))
                       (sort-by (juxt (comp - :nights_together) #(first (:players %))))
                       (take 2)
                       (mapv #(select-keys % [:players :nights_together :nights_both_present])))
       :rarest_pair (->> features
                         (filter #(pos? (:nights_both_present %)))
                         (sort-by (juxt :nights_together (comp - :nights_both_present)))
                         first
                         (#(when % (select-keys % [:players :nights_together :nights_both_present]))))
       :champion_pairs (->> features
                            (filter #(pos? (:titles_together %)))
                            (sort-by (juxt (comp - :titles_together) (comp - :title_opportunities)))
                            (take 2)
                            (mapv #(select-keys % [:players :titles_together :title_opportunities])))
       :assist_links (->> features
                          (mapcat (fn [{:keys [players assists_first_to_second assists_second_to_first]}]
                                    (let [[a b] players]
                                      (cond-> []
                                        (pos? assists_first_to_second) (conj {:from a :to b :count assists_first_to_second})
                                        (pos? assists_second_to_first) (conj {:from b :to a :count assists_second_to_first})))))
                          (sort-by (juxt (comp - :count) :from :to))
                          (take 3)
                          vec)})))

(defn- describe-team
  [{:keys [index team-name players anchor-ids short-ids sector history-signals mean-grade]}]
  (let [team-mean (common/team-mean players :grade)
        sector-means (->> common/position-order
                          (keep (fn [pos]
                                  (when-let [m (sector-mean players pos)]
                                    [pos (common/round2 m)])))
                          (into {}))]
    (merge
     {:index index
      :name team-name
      :mean (common/round2 team-mean)
      :total (common/round2 (reduce + 0.0 (map #(double (or (:grade %) 0.0)) players)))
      :formation (common/formation players)
      :diff_to_squad_mean (common/round4 (- team-mean mean-grade))
      :sector_means sector-means
      :main_sector sector
      :anchors (mapv :name (filter #(anchor-ids (:id %)) players))
      :short_history (mapv :name (filter #(short-ids (:id %)) players))
      :players (mapv (fn [p]
                       {:id (:id p)
                        :name (:name p)
                        :position (common/normalize-position (:position p))
                        :position_code (common/position-code (common/normalize-position (:position p)))
                        :grade (common/round2 (or (:grade p) 0.0))
                        :is_anchor (boolean (anchor-ids (:id p)))
                        :short_history (boolean (short-ids (:id p)))
                        :provisional_grade (boolean (:provisional-grade p))})
                     (sort-by common/by-position-then-grade players))}
     (when history-signals
       (let [features (history/team-features history-signals (map :id players))]
         (merge {:titles (mapv #(select-keys % [:name :titles :title_opportunities]) (:players features))}
                (pair-evidence history-signals (map :id players))))))))

(defn draw
  "Runs the tactical-balance draw.

   `players` are maps with :id, :name, :position and :grade. Returns the chosen
   division plus the evidence the UI shows as justifications."
  [players team-names {:keys [use-history history-weight history-signals tolerance]
                       :or {use-history true history-weight 0.20 tolerance default-tolerance}}]
  (let [num-teams (count team-names)
        ;; Nights played feed the "spread out the little-observed" rule, which
        ;; applies whether or not the history signals are weighted in.
        signals history-signals
        weight (if use-history history-weight 0.0)
        sector (main-sector players)
        anchor-ids (anchors players num-teams)
        short-ids (if signals
                    (history/short-history-players signals (map :id players) short-history-threshold)
                    #{})
        mean-grade (common/team-mean players :grade)
        score-history (when signals (history/scorer signals))
        context {:anchor-ids anchor-ids
                 :short-ids short-ids
                 :sector sector
                 :tolerance tolerance
                 :history-weight weight
                 :score-history score-history}
        [teams chosen-cost] (search players num-teams context)
        ;; The same search without the history term, so the report can say
        ;; whether looking at history actually moved anyone.
        [baseline-teams baseline-cost] (if (pos? weight)
                                         (search players num-teams (assoc context :history-weight 0.0))
                                         [teams chosen-cost])
        [anchor-violations tolerance-excess concentration sector-term overall-gap] chosen-cost
        sector-gap (- sector-term (* weight
                                     (if score-history
                                       (:penalty (score-history (map #(map :id %) teams)))
                                       0.0)))
        moved (when (pos? weight)
                (let [team-of (fn [division] (into {} (for [[idx team] (map-indexed vector division)
                                                            p team]
                                                        [(:id p) idx])))
                      before (team-of baseline-teams)
                      after (team-of teams)]
                  (->> players
                       (keep (fn [p]
                               (when (not= (before (:id p)) (after (:id p)))
                                 {:name (:name p)
                                  :from (inc (before (:id p)))
                                  :to (inc (after (:id p)))})))
                       vec)))]
    {:algorithm algorithm-name
     :teams (mapv (fn [idx team]
                    (describe-team {:index idx
                                    :team-name (nth team-names idx)
                                    :players team
                                    :anchor-ids anchor-ids
                                    :short-ids short-ids
                                    :sector sector
                                    ;; Pair and title evidence is only reported
                                    ;; when it actually took part in the choice.
                                    :history-signals (when use-history signals)
                                    :mean-grade mean-grade}))
                  (range num-teams)
                  teams)
     :metrics {:squad_mean (common/round2 mean-grade)
               :team_mean_gap (common/round4 overall-gap)
               :sector_gap (common/round4 sector-gap)
               :main_sector sector
               :tolerance tolerance
               :tolerance_respected (zero? tolerance-excess)
               :anchors_respected (zero? anchor-violations)
               :max_short_history_per_team (long concentration)
               :short_history_threshold short-history-threshold
               :anchors (mapv :name (filter #(anchor-ids (:id %)) players))
               :short_history_players (mapv :name (filter #(short-ids (:id %)) players))
               :restarts restarts}
     :history (if (and signals use-history)
                (let [chosen (score-history (map #(map :id %) teams))
                      baseline (score-history (map #(map :id %) baseline-teams))]
                  {:enabled true
                   :weight history-weight
                   :penalty (common/round4 (:penalty chosen))
                   :components (into {} (map (fn [[k v]] [k (common/round4 v)])) (:components chosen))
                   :baseline_penalty (common/round4 (:penalty baseline))
                   :baseline_components (into {} (map (fn [[k v]] [k (common/round4 v)])) (:components baseline))
                   :baseline_sector_gap (common/round4 (nth baseline-cost 4))
                   :changed_vs_baseline (boolean (seq moved))
                   :moves (or moved [])
                   :coverage (:coverage signals)})
                {:enabled false})}))
