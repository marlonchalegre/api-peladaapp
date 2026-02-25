(ns api-peladaapp.logic.schedule
  (:require
   [clojure.math.combinatorics :as combo]
   [clojure.set :as set]))

(defn update-stats-for-play [stats team-id role]
  (let [current-stats (get stats team-id)
        consecutive-plays (:consecutive-plays current-stats 0)
        is-double (pos? consecutive-plays)]
    (-> stats
        (update-in [team-id :played] (fnil inc 0))
        (assoc-in [team-id :consecutive-rests] 0)
        (update-in [team-id :consecutive-plays] (fnil inc 0))
        (update-in [team-id :doubles-count] (if is-double inc identity))
        (assoc-in [team-id :last-role] role)
        (update-in [team-id role] (fnil inc 0)))))

(defn update-stats-for-rest [stats team-id]
  (-> stats
      (assoc-in [team-id :consecutive-plays] 0)
      (update-in [team-id :consecutive-rests] (fnil inc 0))
      (assoc-in [team-id :last-role] nil)))

(defn valid-match? [team-stats matches-per-team teams-in-match all-teams-count]
  (let [home-team (first teams-in-match)
        away-team (second teams-in-match)
        home-stats (get team-stats home-team)
        away-stats (get team-stats away-team)

        ;; Calculate potential new doubles count
        home-double? (pos? (:consecutive-plays home-stats 0))
        away-double? (pos? (:consecutive-plays away-stats 0))
        home-new-doubles (if home-double? (inc (:doubles-count home-stats 0)) (:doubles-count home-stats 0))
        away-new-doubles (if away-double? (inc (:doubles-count away-stats 0)) (:doubles-count away-stats 0))

        all-doubles (map :doubles-count (vals team-stats))
        min-doubles (if (seq all-doubles) (apply min all-doubles) 0)

        ;; Home/Away Balance Check
        new-home-count (inc (get home-stats :home 0))
        new-away-count (inc (get away-stats :away 0))
        home-balance (- new-home-count (get home-stats :away 0))
        away-balance (- new-away-count (get away-stats :home 0))]
    (and
     ;; Check for home-team
     (< (:played home-stats 0) matches-per-team)
     (or (= all-teams-count 2) (< (:consecutive-plays home-stats 0) 2))
     (or (not home-double?) (not= (:last-role home-stats) :home))
     (<= home-balance 2) ;; Threshold for home/away imbalance

     ;; Check for away-team
     (< (:played away-stats 0) matches-per-team)
     (or (= all-teams-count 2) (< (:consecutive-plays away-stats 0) 2))
     (or (not away-double?) (not= (:last-role away-stats) :away))
     (<= away-balance 2) ;; Threshold for home/away imbalance

     ;; Check other teams for consecutive rests
     (every? (fn [[team-id stats]]
               (if (or (= team-id home-team) (= team-id away-team))
                 true
                 (< (:consecutive-rests stats 0) 2)))
             (apply dissoc team-stats [home-team away-team]))

     ;; Fairness check for doubles
     (<= (- home-new-doubles min-doubles) 1)
     (<= (- away-new-doubles min-doubles) 1))))

(defn- find-schedule
  [schedule team-stats all-teams total-matches matches-per-team remaining-matches]
  (if (>= (count schedule) total-matches)
    schedule
    (let [distinct-matches (distinct remaining-matches)]
      (loop [candidates distinct-matches]
        (if-let [match (first candidates)]
          ;; Try both orientations for each match
          (let [orientations [[(:home match) (:away match)] [(:away match) (:home match)]]
                result (some (fn [[h a]]
                               (if (valid-match? team-stats matches-per-team [h a] (count all-teams))
                                 (let [playing-teams {h :home a :away}
                                       resting-teams (set/difference all-teams (set [h a]))
                                       next-team-stats (reduce-kv update-stats-for-play team-stats playing-teams)
                                       next-team-stats (reduce update-stats-for-rest next-team-stats resting-teams)

                                       idx (.indexOf remaining-matches match)
                                       next-remaining (vec (concat (subvec remaining-matches 0 idx)
                                                                   (subvec remaining-matches (inc idx))))]
                                   (find-schedule (conj schedule {:home h :away a}) next-team-stats all-teams total-matches matches-per-team next-remaining))
                                 nil))
                             orientations)]
            (if (seq result)
              result
              (recur (rest candidates))))
          nil)))))

(defn- pair-round [teams]
  (let [n (count teams)
        half (/ n 2)
        left (subvec teams 0 half)
        right (->> (subvec teams half n) reverse vec)]
    (map vector left right)))

(defn- circle-method-rounds
  [teams]
  (let [n (count teams)
        _ (assert (even? n) "Number of teams must be even")
        t (vec teams)
        head (first t)
        tail (vec (rest t))]
    (loop [i 0
           left [head]
           right tail
           rounds []]
      (if (= i (dec n))
        rounds
        (let [current (vec (concat left right))
              pairs (pair-round current)
              rotated (if (<= 2 (count current))
                        (vec (concat [head] [(last current)] (subvec current 1 (dec (count current)))))
                        current)]
          (recur (inc i)
                 [(first rotated)]
                 (vec (rest rotated))
                 (conj rounds pairs)))))))

(defn- generate-all-possible-matches [team-ids]
  (->> (combo/combinations team-ids 2)
       (mapv (fn [[h a]] {:home h :away a}))))

(defn- reorder-for-natural-start
  "Reorder teams so that circle method starts with 1x2, 3x4, etc.
   Pattern for even n: [1 3 5 ... n-1 n n-2 ... 4 2]"
  [teams]
  (let [n (count teams)
        sorted (sort teams)]
    (if (even? n)
      (let [odds (take-nth 2 sorted)
            evens (take-nth 2 (rest sorted))]
        (vec (concat odds (reverse evens))))
      (vec sorted))))

(defn schedule-matches-with-limit
  "Backtracking scheduler to find a valid sequence of matches."
  [team-ids matches-per-team]
  (let [teams (reorder-for-natural-start team-ids)
        n (count teams)
        total-matches (if (and n (pos? n) matches-per-team) (quot (* n matches-per-team) 2) 0)
        all-teams-set (set teams)
        initial-stats (zipmap teams (repeat {:played 0 :consecutive-plays 0 :consecutive-rests 0 :doubles-count 0 :last-role nil :home 0 :away 0}))

        round-robin-matches (if (even? n)
                              (mapv (fn [[h a]] {:home h :away a}) (mapcat identity (circle-method-rounds teams)))
                              (generate-all-possible-matches teams))

        all-possible-matches (vec (take (* 2 total-matches) (cycle round-robin-matches)))

        result (find-schedule [] initial-stats all-teams-set total-matches matches-per-team all-possible-matches)]

    (if (and (seq result) (= (count result) total-matches))
      result
      (if (seq result)
        result
        (throw (ex-info "Could not find a valid schedule with the given constraints."
                        {:type :bad-request}))))))

(defn schedule-matches
  [team-ids]
  (let [n (count team-ids)
        matches-per-team (dec n)]
    (schedule-matches-with-limit team-ids matches-per-team)))
