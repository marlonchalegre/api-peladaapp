(ns api-100folego.logic.schedule
  (:require [schema.core :as s]))

(defn- rotate [coll]
  (let [head (first coll)
        tail (rest coll)]
    (concat [head] (take (dec (count tail)) (rest tail)) [(last tail)])))

(defn- pair-round [teams]
  (let [n (count teams)
        half (/ n 2)
        left (subvec teams 0 half)
        right (->> (subvec teams half n) reverse vec)]
    (map vector left right)))

(defn- circle-method-rounds
  "Generate round-robin rounds using the circle method.
   teams: vector of team ids (even count). Returns a seq of rounds, each a seq of [home away]."
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
              ;; rotate keeping head fixed
              rotated (if (<= 2 (count current))
                        (vec (concat [head] [(last current)] (subvec current 1 (dec (count current)))))
                        current)]
          (recur (inc i)
                 [(first rotated)]
                 (vec (rest rotated))
                 (conj rounds pairs)))))))

(defn schedule-matches
  "Return a vector of matches as maps {:home team-id :away team-id}, with a sequence order that tries
   to avoid >2 consecutive plays or rests. For now we flatten rounds and interleave pairs from different rounds
   to reduce consecutive constraints."
  [team-ids]
  (let [rounds (circle-method-rounds (vec team-ids))
        ;; Interleave across rounds by taking first match from each round, then second, etc.
        max-matches-per-round (apply max (map count rounds))
        seq-matches (for [i (range max-matches-per-round)
                          r rounds
                          :let [m (nth r i nil)]
                          :when m]
                      m)]
    (mapv (fn [[h a]] {:home h :away a}) seq-matches)))

(defn schedule-matches-with-limit
  "Greedy scheduler with coverage: ensure every pair of teams meets at least
  once, and respect max 2 consecutive plays or rests. Each team plays up to
  target = max(matches-per-team, (count teams)-1). Returns a vector."
  [team-ids matches-per-team]
  (let [teams (vec team-ids)
        n (count teams)
        target (max (or matches-per-team 0) (dec n))
        init (zipmap teams (repeat {:played 0 :play-streak 0 :rest-streak 0}))
        required (->> (schedule-matches team-ids)
                      (map (fn [{:keys [home away]}]
                             [(min home away) (max home away)]))
                      set)]
    (loop [state init
           need required
           acc []]
      (if (every? (fn [[_ {:keys [played]}]] (>= played target)) state)
        (vec acc)
        (let [all-pairs (for [i (range n)
                              j (range (inc i) n)]
                          [(teams i) (teams j)])
              scored (->> all-pairs
                          (filter (fn [[a b]]
                                    (let [{pa :played sa :play-streak} (state a)
                                          {pb :played sb :play-streak} (state b)]
                                      (and (< pa target) (< pb target) (< sa 2) (< sb 2)))))
                          (map (fn [[a b]]
                                 (let [{ra :rest-streak pa :played} (state a)
                                       {rb :rest-streak pb :played} (state b)
                                       must? (contains? need [(min a b) (max a b)])]
                                   [[a b] [(if must? 1 0) (- target pa) (- target pb) ra rb]])))
                          (sort-by (fn [[_ [must need-a need-b rest-a rest-b]]]
                                     [(- must) (- need-a) (- need-b) (- rest-a) (- rest-b)])))
              pair (ffirst scored)]
          (if (nil? pair)
            (vec acc)
            (let [[a b] pair
                  updated (-> state
                              (update a (fn [{:keys [played]}]
                                          {:played (inc played)
                                           :play-streak (inc (:play-streak (state a)))
                                           :rest-streak 0}))
                              (update b (fn [{:keys [played]}]
                                          {:played (inc played)
                                           :play-streak (inc (:play-streak (state b)))
                                           :rest-streak 0})))
                  state' (into {}
                               (for [[t s] updated]
                                 (if (or (= t a) (= t b))
                                   [t s]
                                   [t {:played (:played s)
                                       :play-streak 0
                                       :rest-streak (inc (:rest-streak s))}])))
                  need' (disj need [(min a b) (max a b)])]
              (recur state' need' (conj acc {:home a :away b})))))))))

