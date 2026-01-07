(ns api-peladaapp.logic.schedule
  (:require
   [clojure.math.combinatorics :as combo]
   [clojure.set :as set]))

(defn update-stats-for-play [stats team-id]
  (let [current-stats (get stats team-id)
        consecutive-plays (:consecutive-plays current-stats 0)
        is-double (pos? consecutive-plays)]
    (-> stats
        (update-in [team-id :played] (fnil inc 0))
        (assoc-in [team-id :consecutive-rests] 0)
        (update-in [team-id :consecutive-plays] (fnil inc 0))
        (update-in [team-id :doubles-count] (if is-double inc identity)))))

(defn update-stats-for-rest [stats team-id]
  (-> stats
      (assoc-in [team-id :consecutive-plays] 0)
      (update-in [team-id :consecutive-rests] (fnil inc 0))))

(defn valid-match? [team-stats matches-per-team teams-in-match]
  (let [team1 (first teams-in-match)
        team2 (second teams-in-match)
        stats1 (get team-stats team1)
        stats2 (get team-stats team2)

        ;; Calculate potential new doubles count
        t1-double? (pos? (:consecutive-plays stats1 0))
        t2-double? (pos? (:consecutive-plays stats2 0))
        t1-new-doubles (if t1-double? (inc (:doubles-count stats1 0)) (:doubles-count stats1 0))
        t2-new-doubles (if t2-double? (inc (:doubles-count stats2 0)) (:doubles-count stats2 0))

        all-doubles (map :doubles-count (vals team-stats))
        min-doubles (if (seq all-doubles) (apply min all-doubles) 0)]
    (and
     ;; Check for team1
     (< (:played stats1 0) matches-per-team)
     (< (:consecutive-plays stats1 0) 2)
     ;; Check for team2
     (< (:played stats2 0) matches-per-team)
     (< (:consecutive-plays stats2 0) 2)
     ;; Check other teams for consecutive rests
     (every? (fn [[team-id stats]]
               (if (or (= team-id team1) (= team-id team2))
                 true
                 (< (:consecutive-rests stats 0) 2)))
             (apply dissoc team-stats teams-in-match))
     ;; Fairness check for doubles
     (<= (- t1-new-doubles min-doubles) 1)
     (<= (- t2-new-doubles min-doubles) 1))))

(defn- find-schedule
  [schedule team-stats all-teams total-matches matches-per-team remaining-matches]
  (if (empty? remaining-matches)
    schedule
    (let [distinct-matches (distinct remaining-matches)]
      (loop [candidates distinct-matches]
        (when-let [match (first candidates)]
          (let [teams-in-match [(:home match) (:away match)]]
            (if (valid-match? team-stats matches-per-team teams-in-match)
              (let [playing-teams (set teams-in-match)
                    resting-teams (set/difference all-teams playing-teams)
                    next-team-stats (reduce update-stats-for-play team-stats playing-teams)
                    next-team-stats (reduce update-stats-for-rest next-team-stats resting-teams)

                    idx (.indexOf remaining-matches match)
                    next-remaining (vec (concat (subvec remaining-matches 0 idx)
                                                (subvec remaining-matches (inc idx))))

                    result (find-schedule (conj schedule match) next-team-stats all-teams total-matches matches-per-team next-remaining)]
                (if (seq result)
                  result
                  (recur (rest candidates))))
              (recur (rest candidates)))))))))

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

(defn schedule-matches-with-limit
  "Backtracking scheduler to find a valid sequence of matches."
  [team-ids matches-per-team]
  (when (and matches-per-team (odd? (* (count team-ids) matches-per-team)))
    (throw (ex-info "Total number of plays must be even."
                    {:type :bad-request})))
  (let [teams (vec team-ids)
        n (count teams)
        total-matches (if (and n (pos? n) matches-per-team) (quot (* n matches-per-team) 2) 0)
        all-teams-set (set teams)
        initial-stats (zipmap teams (repeat {:played 0 :consecutive-plays 0 :consecutive-rests 0 :doubles-count 0}))

        round-robin-matches (if (even? n)
                              (mapv (fn [[h a]] {:home h :away a}) (mapcat identity (circle-method-rounds teams)))
                              (generate-all-possible-matches teams))

        all-possible-matches (vec (take total-matches (cycle round-robin-matches)))]
    (find-schedule [] initial-stats all-teams-set total-matches matches-per-team all-possible-matches)))

(defn schedule-matches
  [team-ids]
  (let [n (count team-ids)
        matches-per-team (dec n)]
    (schedule-matches-with-limit team-ids matches-per-team)))