(ns api-peladaapp.logic.draw-common
  "Primitives shared by the team draw algorithms.

   Both algorithms search over the same space: divisions that keep every
   position spread as evenly as the roster allows. They differ in what they
   consider a good division, which is the cost function each one supplies."
  (:require
   [clojure.math :as math]
   [clojure.string :as str]))

(def goalkeeper "Goalkeeper")
(def defender "Defender")
(def midfielder "Midfielder")
(def striker "Striker")

(def position-order
  "Reading order for reports; also the order sectors are listed in."
  [goalkeeper defender midfielder striker])

(def position-code
  {goalkeeper "G" defender "Z" midfielder "M" striker "A"})

(def ^:private position-set (set position-order))

(defn normalize-position
  "Players with no position recorded are treated as midfielders, which is what
   the rest of the app assumes when it has to place someone."
  [position]
  (let [p (some-> position name)]
    (if (contains? position-set p) p midfielder)))

(defn position-index
  [position]
  (.indexOf ^java.util.List position-order (normalize-position position)))

(defn by-position-then-grade
  "Report order: sectors from the back, strongest first inside each."
  [player]
  [(position-index (:position player)) (- (double (or (:grade player) 0.0)))])

(defn strongest-first
  "Highest grade first, name as a stable tie-break."
  [players]
  (sort-by (juxt #(- (double (or (:grade %) 0.0))) :name) players))

(defn pairs-by
  "Every unordered pair of `coll`, ordered by `key-fn`."
  [key-fn coll]
  (let [items (vec (sort-by key-fn coll))]
    (for [i (range (count items))
          j (range (inc i) (count items))]
      [(nth items i) (nth items j)])))

(defn pairs
  "Every unordered pair of a collection, in a stable order."
  [coll]
  (let [items (vec (sort coll))]
    (for [i (range (count items))
          j (range (inc i) (count items))]
      [(nth items i) (nth items j)])))

(defn seeded-random
  [seed]
  (java.util.Random. (long seed)))

(defn- next-int
  [^java.util.Random rng n]
  (.nextInt rng (int n)))

(defn- shuffle-with
  [^java.util.Random rng coll]
  (let [items (java.util.ArrayList. ^java.util.Collection (vec coll))]
    (java.util.Collections/shuffle items rng)
    (vec items)))

(defn sector-players
  [team position]
  (filter #(= position (normalize-position (:position %))) team))

(defn balanced-distribution
  "Opening draft: each position group is dealt strongest first, and every player
   goes to the team that has the fewest of that position, breaking ties by the
   emptiest team. Teams therefore end up the same size (within one player) with
   each position spread as evenly as the squad allows.

   Team size is capped at ceil(players / teams), which is what keeps a greedy
   position choice from stacking one side."
  [players num-teams rng]
  (let [capacity (long (math/ceil (/ (double (count players)) num-teams)))
        groups (->> players
                    (group-by #(normalize-position (:position %)))
                    (sort-by (comp position-index key)))
        order (shuffle-with rng (range num-teams))]
    (reduce (fn [teams [position group]]
              (let [ordered (->> group
                                 (shuffle-with rng)
                                 (sort-by #(- (double (or (:grade %) 0.0)))))]
                (reduce (fn [acc player]
                          (let [target (->> order
                                            (remove #(>= (count (nth acc %)) capacity))
                                            (sort-by (fn [t]
                                                       [(count (sector-players (nth acc t) position))
                                                        (count (nth acc t))]))
                                            first)]
                            (if target
                              (update acc target conj player)
                              ;; Only reachable if capacity was under-counted;
                              ;; never drop a player because of it.
                              (update acc (apply min-key #(count (nth acc %)) order) conj player))))
                        teams
                        ordered)))
            (vec (repeat num-teams []))
            groups)))

(defn- swap-candidates
  "Positions of two players that share a position group but sit on different
   teams. Swapping them keeps every team's shape intact."
  [teams]
  (let [indexed (for [[t team] (map-indexed vector teams)
                      [i player] (map-indexed vector team)]
                  [t i (normalize-position (:position player))])]
    (vec (for [[t1 i1 pos1] indexed
               [t2 i2 pos2] indexed
               :when (and (< t1 t2) (= pos1 pos2))]
           [t1 i1 t2 i2]))))

(defn apply-swap
  [teams [t1 i1 t2 i2]]
  (let [a (get-in teams [t1 i1])
        b (get-in teams [t2 i2])]
    (-> teams
        (assoc-in [t1 i1] b)
        (assoc-in [t2 i2] a))))

(defn hill-climb
  "Deterministic descent: repeatedly take the single swap that improves `cost`
   the most, and stop when no swap does. `better?` compares two cost values."
  [teams cost better? max-steps]
  ;; A swap only ever trades two players of the same position, so no slot ever
  ;; changes position and the candidate list is the same on every step.
  (let [candidates (swap-candidates teams)]
    (loop [current teams
           current-cost (cost teams)
           step 0]
      (if (>= step max-steps)
        [current current-cost]
        (let [[best best-cost]
              (reduce (fn [[acc acc-cost] swap]
                        (let [candidate (apply-swap current swap)
                              candidate-cost (cost candidate)]
                          (if (better? candidate-cost acc-cost)
                            [candidate candidate-cost]
                            [acc acc-cost])))
                      [current current-cost]
                      candidates)]
          (if (better? best-cost current-cost)
            (recur best best-cost (inc step))
            [current current-cost]))))))

(defn annealing
  "Randomised descent that accepts a worse division once in a while, so the
   search can leave a local minimum. Seeded, therefore reproducible."
  [teams cost better? {:keys [steps accept-worse rng]}]
  ;; Same invariant as hill-climb: the candidate list never changes.
  (let [candidates (swap-candidates teams)]
    (loop [current teams
           current-cost (cost teams)
           best current
           best-cost current-cost
           step 0]
      (if (or (>= step steps) (empty? candidates))
        [best best-cost]
        (let [swap (nth candidates (next-int rng (count candidates)))
              candidate (apply-swap current swap)
              candidate-cost (cost candidate)
              improved? (better? candidate-cost current-cost)
              keep? (or improved? (< (.nextDouble ^java.util.Random rng) accept-worse))
              [next-teams next-cost] (if keep? [candidate candidate-cost] [current current-cost])
              [next-best next-best-cost] (if (better? next-cost best-cost)
                                           [next-teams next-cost]
                                           [best best-cost])]
          (recur next-teams next-cost next-best next-best-cost (inc step)))))))

(defn mean
  [coll]
  (if (seq coll) (/ (reduce + 0.0 coll) (count coll)) 0.0))

(defn spread
  [coll]
  (if (seq coll) (- (apply max coll) (apply min coll)) 0.0))

(defn team-mean
  [team key-fn]
  (mean (map #(double (or (key-fn %) 0.0)) team)))

(defn round2
  [x]
  (/ (math/round (* 100.0 (double x))) 100.0))

(defn round4
  [x]
  (/ (math/round (* 10000.0 (double x))) 10000.0))

(defn formation
  "Compact shape of a team, e.g. 2-2-1, listing only the sectors it uses."
  [team]
  (let [counts (frequencies (map #(normalize-position (:position %)) team))]
    (->> position-order
         (keep (fn [pos] (when-let [n (get counts pos)] (str n))))
         (str/join "-"))))
