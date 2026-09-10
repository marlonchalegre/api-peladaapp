(ns api-peladaapp.logic.draw-gemini
  "Cost-driven draw: 'Química & Rotatividade'.

   Ported from pelada-stats/sorteador_100folego.py and generalised from its
   fixed 4x5 roster to any number of teams and squad size.

   Where the tactical draw applies rules in order, this one adds everything
   into a single cost and anneals towards its minimum. It also judges players
   by more than the admin grade: a Bayesian rating built from the stars they
   received, plus attacking and defending indices derived from goals, assists
   and goals conceded. On top of that, chemistry:

     • break up duos that have won three or more nights together;
     • reward pairings that have never shared a team but often faced each other;
     • level accumulated titles across teams;
     • level how long each side has gone without winning.

   Turning chemistry off leaves the plain numeric balance — the classic count."
  (:require
   [api-peladaapp.logic.draw-common :as common]
   [api-peladaapp.logic.draw-history :as history]))

(def algorithm-name "gemini")

(def weights
  "Relative pull of each term in the cost. Sector balance dominates; chemistry
   corrects at the margin rather than overriding a lopsided division."
  {:overall 35.0
   :defense 30.0
   :offense 25.0
   :champion-duo 9.0
   :novelty 5.0
   :titles 1.5
   :drought 1.8})

(def champion-duo-threshold
  "Nights won together from which a duo starts being treated as a clique."
  3)

(def novelty-opposition-threshold
  "Times two players faced each other before a first pairing counts as new."
  8)

(def ^:private vote-prior-weight 15.0)
(def ^:private restarts 8)
(def ^:private annealing-steps 900)
(def ^:private accept-worse 0.05)

(defn- clamp
  [x low high]
  (max low (min high x)))

(defn- bayesian-rating
  "Average stars pulled towards the organization mean. A player with few votes
   sits near the mean instead of at an extreme."
  [signals player-id]
  (let [{:keys [sum count]} (get-in signals [:vote-stars player-id] {})
        prior (:vote-mean signals 3.5)]
    (/ (+ (* vote-prior-weight prior) (or sum 0))
       (+ vote-prior-weight (or count 0)))))

(defn profile
  "Attacking and defending indices for one player.

   With no history the indices collapse to the admin grade, so the algorithm
   still runs on a fresh organization — it just has less to say."
  [signals player]
  (let [grade (double (or (:grade player) 7.0))
        position (common/normalize-position (:position player))]
    (if (nil? signals)
      {:id (:id player) :name (:name player) :position position
       :provisional-grade (boolean (:provisional-grade player))
       :grade grade :overall grade :offense grade :defense grade
       :matches 0 :goals 0 :assists 0 :titles 0 :drought 0 :bayes_vote nil}
      (let [id (:id player)
            rating (bayesian-rating signals id)
            vote-grade (* 2.0 rating)
            overall (+ (* 0.40 grade) (* 0.50 vote-grade) (* 0.10 7.0))
            matches (get-in signals [:matches-played id] 0)
            goals (get-in signals [:goals id] 0)
            assists (get-in signals [:assist-count id] 0)
            conceded (get-in signals [:goals-conceded id] 0)
            ;; Under five matches the rates are noise; use neutral defaults.
            enough? (>= matches 5)
            ga-rate (if enough? (/ (double (+ goals assists)) matches) 0.35)
            concede-rate (if enough? (/ (double conceded) matches) 1.20)
            offense (+ (* 0.5 overall) (* 0.5 (clamp (+ 4.0 (* ga-rate 7.0)) 2.0 10.0)))
            defense (+ (* 0.5 overall) (* 0.5 (clamp (- 10.0 (* (- concede-rate 0.8) 5.0)) 2.0 10.0)))
            [offense defense] (condp = position
                                common/defender [(- offense 0.5) (+ defense 0.5)]
                                common/striker [(+ offense 0.5) (- defense 0.5)]
                                [offense defense])]
        {:id id :name (:name player) :position position
         :provisional-grade (boolean (:provisional-grade player))
         :grade grade
         :overall overall
         :offense offense
         :defense defense
         :matches matches
         :goals goals
         :assists assists
         :conceded conceded
         :titles (get-in signals [:titles id] 0)
         :drought (get-in signals [:drought id] 0)
         :bayes_vote rating}))))

(defn- chemistry-terms
  [signals teams]
  (let [pair-stat (fn [m a b] (get m (history/duo (:id a) (:id b)) 0))
        champion-penalty
        (reduce + 0.0
                (for [team teams
                      [a b] (common/pairs-by :id team)
                      :let [titles (pair-stat (:pair-titles signals) a b)]
                      :when (>= titles champion-duo-threshold)]
                  (* (- titles (dec champion-duo-threshold)) (:champion-duo weights))))
        novelty-bonus
        (reduce + 0.0
                (for [team teams
                      [a b] (common/pairs-by :id team)
                      :let [together (pair-stat (:together signals) a b)
                            opposite (pair-stat (:opposite signals) a b)]
                      :when (and (zero? together) (>= opposite novelty-opposition-threshold))]
                  (:novelty weights)))
        titles-spread (common/spread (map (fn [team] (reduce + 0 (map :titles team))) teams))
        drought-spread (common/spread (map (fn [team] (reduce + 0 (map :drought team))) teams))]
    {:champion_penalty champion-penalty
     :novelty_bonus novelty-bonus
     :titles_penalty (* titles-spread (:titles weights))
     :drought_penalty (* drought-spread (:drought weights))
     :titles_spread titles-spread
     :drought_spread drought-spread}))

(defn- base-terms
  [teams]
  (let [overall (map #(common/team-mean % :overall) teams)
        defense (map #(common/team-mean % :defense) teams)
        offense (map #(common/team-mean % :offense) teams)]
    {:overall_spread (common/spread overall)
     :defense_spread (common/spread defense)
     :offense_spread (common/spread offense)
     :overall_means (mapv common/round2 overall)
     :defense_means (mapv common/round2 defense)
     :offense_means (mapv common/round2 offense)}))

(defn- cost-fn
  [signals chemistry?]
  (fn [teams]
    (let [{:keys [overall_spread defense_spread offense_spread]} (base-terms teams)
          base (+ (* overall_spread (:overall weights))
                  (* defense_spread (:defense weights))
                  (* offense_spread (:offense weights)))]
      (if-not (and chemistry? signals)
        base
        (let [{:keys [champion_penalty novelty_bonus titles_penalty drought_penalty]}
              (chemistry-terms signals teams)]
          (+ base champion_penalty titles_penalty drought_penalty (- novelty_bonus)))))))

(defn- search
  [profiles num-teams cost]
  (->> (range restarts)
       (map (fn [seed]
              (let [rng (common/seeded-random (+ 104729 (* 37 seed)))
                    start (common/balanced-distribution profiles num-teams rng)]
                (common/annealing start cost < {:steps annealing-steps
                                                :accept-worse accept-worse
                                                :rng rng}))))
       (sort-by second)
       first))

(defn- describe-team
  [index team-name team signals chemistry?]
  (let [top-winner (when (seq team) (apply max-key :titles team))
        longest-drought (when (seq team) (apply max-key :drought team))
        clique (when (and chemistry? signals)
                 (->> (common/pairs-by :id team)
                      (keep (fn [[a b]]
                              (let [titles (get (:pair-titles signals) (history/duo (:id a) (:id b)) 0)]
                                (when (pos? titles)
                                  {:players [(:name a) (:name b)] :titles_together titles}))))
                      (sort-by (comp - :titles_together))
                      (take 2)
                      vec))
        fresh (when (and chemistry? signals)
                (->> (common/pairs-by :id team)
                     (keep (fn [[a b]]
                             (let [key (history/duo (:id a) (:id b))
                                   together (get (:together signals) key 0)
                                   opposite (get (:opposite signals) key 0)]
                               (when (and (zero? together) (>= opposite novelty-opposition-threshold))
                                 {:players [(:name a) (:name b)] :faced_each_other opposite}))))
                     (sort-by (comp - :faced_each_other))
                     (take 2)
                     vec))]
    (cond-> {:index index
             :name team-name
             :mean (common/round2 (common/team-mean team :grade))
             :overall (common/round2 (common/team-mean team :overall))
             :defense (common/round2 (common/team-mean team :defense))
             :offense (common/round2 (common/team-mean team :offense))
             :formation (common/formation team)
             :players (mapv (fn [p]
                              {:id (:id p)
                               :name (:name p)
                               :position (:position p)
                               :position_code (common/position-code (:position p))
                               :grade (common/round2 (:grade p))
                               :provisional_grade (boolean (:provisional-grade p))
                               :overall (common/round2 (:overall p))
                               :offense (common/round2 (:offense p))
                               :defense (common/round2 (:defense p))
                               :titles (:titles p)
                               :drought (:drought p)
                               :matches (:matches p)
                               :goals (:goals p)
                               :assists (:assists p)
                               :bayes_vote (some-> (:bayes_vote p) common/round2)})
                            (sort-by common/by-position-then-grade team))}
      chemistry? (assoc :titles_total (reduce + 0 (map :titles team))
                        :drought_total (reduce + 0 (map :drought team))
                        :top_winner (when top-winner
                                      {:name (:name top-winner) :titles (:titles top-winner)})
                        :longest_drought (when longest-drought
                                           {:name (:name longest-drought) :drought (:drought longest-drought)})
                        :champion_pairs (or clique [])
                        :new_pairs (or fresh [])))))

(defn draw
  "Runs the chemistry draw. With `use-history` false it degrades to the classic
   numeric balance the group used before."
  [players team-names {:keys [use-history history-signals]
                       :or {use-history true}}]
  (let [num-teams (count team-names)
        signals (when use-history history-signals)
        chemistry? (boolean signals)
        profiles (mapv #(profile history-signals %) players)
        cost (cost-fn signals chemistry?)
        [teams best-cost] (search profiles num-teams cost)
        base (base-terms teams)
        chemistry (when chemistry? (chemistry-terms signals teams))]
    {:algorithm algorithm-name
     :teams (mapv (fn [idx team] (describe-team idx (nth team-names idx) team signals chemistry?))
                  (range num-teams)
                  teams)
     :metrics (merge {:cost (common/round2 best-cost)
                      :squad_mean (common/round2 (common/team-mean profiles :grade))
                      :grade_gap (common/round4 (common/spread (map #(common/team-mean % :grade) teams)))
                      :overall_gap (common/round4 (:overall_spread base))
                      :defense_gap (common/round4 (:defense_spread base))
                      :offense_gap (common/round4 (:offense_spread base))
                      :overall_means (:overall_means base)
                      :defense_means (:defense_means base)
                      :offense_means (:offense_means base)
                      :restarts restarts
                      :steps annealing-steps}
                     (when chemistry
                       {:titles_spread (:titles_spread chemistry)
                        :drought_spread (:drought_spread chemistry)
                        :champion_penalty (common/round2 (:champion_penalty chemistry))
                        :novelty_bonus (common/round2 (:novelty_bonus chemistry))
                        :titles_penalty (common/round2 (:titles_penalty chemistry))
                        :drought_penalty (common/round2 (:drought_penalty chemistry))}))
     :history (if chemistry?
                {:enabled true
                 :champion_duo_threshold champion-duo-threshold
                 :novelty_opposition_threshold novelty-opposition-threshold
                 :coverage (:coverage signals)}
                {:enabled false})}))
