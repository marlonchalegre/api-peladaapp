(ns api-peladaapp.logic.ils-schedule
  (:require
   [clojure.math.combinatorics :as combo]
   [clojure.set :as set]))

;; --- Cost Function & Rule Validation ---

(defn- get-rest-limit [n]
  (if (<= n 4) 2 (int (Math/ceil (/ n 2.0)))))

(defn- validate-schedule [matches team-ids matches-per-team]
  (let [n (count team-ids)
        rest-limit (get-rest-limit n)
        initial-stats (zipmap team-ids (repeat {:played 0 :consecutive-plays 0 :consecutive-rests 0 :doubles-count 0 :last-role nil :home 0 :away 0}))]
    (reduce
     (fn [{:keys [stats violations]} {:keys [home away]}]
       (let [home-stats (get stats home)
             away-stats (get stats away)
             
             ;; Rule violations
             v1 (if (>= (:played home-stats 0) matches-per-team) 1 0)
             v2 (if (>= (:played away-stats 0) matches-per-team) 1 0)
             v3 (if (and (> n 2) (>= (:consecutive-plays home-stats 0) 2)) 1 0)
             v4 (if (and (> n 2) (>= (:consecutive-plays away-stats 0) 2)) 1 0)
             v5 (if (and (pos? (:consecutive-plays home-stats 0)) (= (:last-role home-stats) :home)) 1 0)
             v6 (if (and (pos? (:consecutive-plays away-stats 0)) (= (:last-role away-stats) :away)) 1 0)
             
             ;; Home/Away balance
             new-home-count (inc (:home home-stats 0))
             new-away-count (inc (:away away-stats 0))
             home-balance (Math/abs (- new-home-count (:away home-stats 0)))
             away-balance (Math/abs (- new-away-count (:home away-stats 0)))
             v7 (if (> home-balance 2) 1 0)
             v8 (if (> away-balance 2) 1 0)
             
             ;; Rest violations (check all other teams)
             other-violations (->> (dissoc stats home away)
                                   (map (fn [[_ s]] (if (>= (:consecutive-rests s 0) rest-limit) 1 0)))
                                   (reduce + 0))
             
             ;; Update stats
             next-stats (-> stats
                            (update-in [home :played] (fnil inc 0))
                            (assoc-in [home :consecutive-rests] 0)
                            (update-in [home :doubles-count] (if (pos? (:consecutive-plays home-stats 0)) inc identity))
                            (update-in [home :consecutive-plays] (fnil inc 0))
                            (assoc-in [home :last-role] :home)
                            (update-in [home :home] (fnil inc 0))
                            
                            (update-in [away :played] (fnil inc 0))
                            (assoc-in [away :consecutive-rests] 0)
                            (update-in [away :doubles-count] (if (pos? (:consecutive-plays away-stats 0)) inc identity))
                            (update-in [away :consecutive-plays] (fnil inc 0))
                            (assoc-in [away :last-role] :away)
                            (update-in [away :away] (fnil inc 0)))
             
             next-stats (reduce (fn [s tid]
                                  (-> s
                                      (assoc-in [tid :consecutive-plays] 0)
                                      (update-in [tid :consecutive-rests] (fnil inc 0))
                                      (assoc-in [tid :last-role] nil)))
                                next-stats
                                (set/difference (set team-ids) (set [home away])))]
         
         {:stats next-stats
          :violations (+ violations v1 v2 v3 v4 v5 v6 v7 v8 other-violations)}))
     {:stats initial-stats :violations 0}
     matches)))

(defn cost [matches team-ids matches-per-team]
  (let [res (validate-schedule matches team-ids matches-per-team)
        doubles (map :doubles-count (vals (:stats res)))
        min-doubles (if (seq doubles) (apply min doubles) 0)
        max-doubles (if (seq doubles) (apply max doubles) 0)
        doubles-violation (if (> (- max-doubles min-doubles) 1) (- max-doubles min-doubles) 0)]
    (+ (:violations res) doubles-violation)))

;; --- Berger Table Algorithm (Circle/Rotation Method) ---

(defn- rotation-rounds [n]
  (let [head 0
        tail (vec (range 1 n))]
    (for [i (range (dec n))]
      (let [step (mod i (count tail))
            rotated-tail (vec (concat (subvec tail (- (count tail) step))
                                      (subvec tail 0 (- (count tail) step))))
            current (vec (concat [head] rotated-tail))
            half (/ n 2)
            top (subvec current 0 half)
            bottom (->> (subvec current half n) reverse vec)]
        (map-indexed (fn [idx t1]
                       (let [t2 (nth bottom idx)]
                         (if (even? (+ i idx))
                           {:home t1 :away t2}
                           {:home t2 :away t1})))
                     top)))))

(defn berger-schedule [team-ids matches-per-team]
  (let [n (count team-ids)
        ids (vec (sort team-ids))
        eff-n (if (even? n) n (inc n))
        
        ;; Manual tables from PDF
        rounds (cond
                 (= n 3) 
                 (let [[t1 t2 t3] ids]
                   [[{:home t1 :away t2}]
                    [{:home t3 :away t1}]
                    [{:home t2 :away t3}]])
                 
                 (= n 4)
                 (let [[t1 t2 t3 t4] ids]
                   [[{:home t1 :away t2} {:home t3 :away t4}]
                    [{:home t1 :away t3} {:home t4 :away t2}]
                    [{:home t4 :away t1} {:home t3 :away t2}]])
                 
                 (or (= n 5) (= n 6))
                 (let [[t1 t2 t3 t4 t5 t6] (if (= n 5) (conj ids :bye) ids)]
                   (map (fn [r] (vec (remove #(or (= (:home %) :bye) (= (:away %) :bye)) r)))
                        [[{:home t2 :away t1} {:home t3 :away t5} {:home t4 :away t6}]
                         [{:home t1 :away t3} {:home t6 :away t2} {:home t5 :away t4}]
                         [{:home t4 :away t1} {:home t3 :away t2} {:home t6 :away t5}]
                         [{:home t1 :away t5} {:home t2 :away t4} {:home t3 :away t6}]
                         [{:home t6 :away t1} {:home t5 :away t2} {:home t4 :away t3}]]))

                 :else
                 ;; General Rotation formula for N > 6
                 (let [rounds-raw (rotation-rounds eff-n)
                       get-id (fn [idx] (if (and (odd? n) (= idx (dec eff-n))) :bye (nth ids idx)))]
                   (map (fn [round]
                          (vec (remove #(or (= (:home %) :bye) (= (:away %) :bye))
                                       (map (fn [{:keys [home away]}] 
                                              {:home (get-id home) :away (get-id away)})
                                            round))))
                        rounds-raw)))
        
        leg1 rounds
        leg2 (map (fn [round] (mapv (fn [{:keys [home away]}] {:home away :away home}) round)) leg1)]
    
    (->> (cycle (concat leg1 leg2))
         (mapcat identity)
         (take (quot (* n matches-per-team) 2))
         (vec))))

;; --- ILS Metaheuristic (Fallback/Optimization) ---

(defn- swap-teams-in-schedule [matches t1 t2]
  (mapv (fn [{:keys [home away]}]
          {:home (cond (= home t1) t2 (= home t2) t1 :else home)
           :away (cond (= away t1) t2 (= away t2) t1 :else away)})
        matches))

(defn- flip-match [matches idx]
  (update matches idx (fn [{:keys [home away]}] {:home away :away home})))

(defn- swap-matches [matches i j]
  (let [m1 (nth matches i)
        m2 (nth matches j)]
    (assoc matches i m2 j m1)))

(defn- local-search [initial-matches team-ids matches-per-team]
  (loop [current initial-matches
         current-cost (cost current team-ids matches-per-team)
         iter 0]
    (if (or (zero? current-cost) (> iter 400))
      current
      (let [moves (concat
                   (map (fn [[t1 t2]] #(swap-teams-in-schedule % t1 t2)) (combo/combinations team-ids 2))
                   (map (fn [i] #(flip-match % i)) (range (count current)))
                   (map (fn [_] (let [i (rand-int (count current)) j (rand-int (count current))] #(swap-matches % i j))) (range 60)))
            
            best-move (->> (shuffle moves)
                           (take 60)
                           (map (fn [f] (let [next (f current)] [next (cost next team-ids matches-per-team)])))
                           (filter (fn [[_ c]] (< c current-cost)))
                           (first))]
        (if best-move
          (recur (first best-move) (second best-move) (inc iter))
          current)))))

(defn- perturb [matches]
  (let [n (count matches)]
    (if (pos? n)
      (let [i (rand-int n)
            j (rand-int n)]
        (-> matches
            (swap-matches i j)
            (flip-match (rand-int n))))
      matches)))

(defn schedule-matches-ils [team-ids matches-per-team]
  (let [n (count team-ids)]
    (if (< n 2)
      []
      (let [initial (berger-schedule team-ids matches-per-team)
            initial-cost (cost initial team-ids matches-per-team)]
        (if (or (and (<= n 4) (<= initial-cost 4))
                (and (<= n 8) (<= initial-cost 2))
                (zero? initial-cost))
          initial
          (loop [best initial
                 best-cost initial-cost
                 iter 0]
            (if (or (zero? best-cost) (> iter 40))
              best
              (let [perturbed (perturb best)
                    optimized (local-search perturbed team-ids matches-per-team)
                    opt-cost (cost optimized team-ids matches-per-team)]
                (if (< opt-cost best-cost)
                  (recur optimized opt-cost (inc iter))
                  (recur best best-cost (inc iter)))))))))))
