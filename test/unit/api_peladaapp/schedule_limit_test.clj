(ns api-peladaapp.schedule-limit-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-peladaapp.logic.schedule :as sch]))

(defn- get-team-states [schedule teams]
  (let [initial-state (zipmap teams (repeat {:played 0 :consecutive-plays 0 :consecutive-rests 0}))]
    (reduce (fn [state match]
              (let [[t1 t2] ((juxt :home :away) match)]
                (reduce (fn [acc-state team-id]
                          (if (or (= t1 team-id) (= t2 team-id))
                            (-> acc-state
                                (update-in [team-id :played] inc)
                                (update-in [team-id :consecutive-plays] (fn [p] (if p (inc p) 1)))
                                (assoc-in [team-id :consecutive-rests] 0))
                            (-> acc-state
                                (update-in [team-id :consecutive-rests] (fn [r] (if r (inc r) 1)))
                                (assoc-in [team-id :consecutive-plays] 0))))
                        state
                        teams)))
            {}
            schedule)))

(deftest schedule-respects-constraints
  (testing "Scheduler respects all constraints with 4 teams and 5 matches per team"
    (let [teams [1 2 3 4]
          matches-per-team 5
          schedule (sch/schedule-matches-with-limit teams matches-per-team)
          total-matches (/ (* (count teams) matches-per-team) 2)]
      (is (= total-matches (count schedule)) "Total number of matches should be correct")

      (let [team-plays (frequencies (mapcat (juxt :home :away) schedule))]
        (is (every? #(= matches-per-team %) (vals team-plays)) "Each team should play the correct number of matches"))

      (loop [remaining-schedule schedule
             previous-matches []
             state (zipmap teams (repeat {:played 0 :consecutive-plays 0 :consecutive-rests 0}))]
        (if (empty? remaining-schedule)
          (is true "Reached end of schedule without violations")
          (let [current-match (first remaining-schedule)
                [t1 t2] ((juxt :home :away) current-match)
                new-state (reduce (fn [acc-state team-id]
                                    (if (or (= t1 team-id) (= t2 team-id))
                                      (-> acc-state
                                          (update-in [team-id :played] inc)
                                          (update-in [team-id :consecutive-plays] (fn [p] (if p (inc p) 1)))
                                          (assoc-in [team-id :consecutive-rests] 0))
                                      (-> acc-state
                                          (update-in [team-id :consecutive-rests] (fn [r] (if r (inc r) 1)))
                                          (assoc-in [team-id :consecutive-plays] 0))))
                                  state
                                  teams)]
            (is (<= (:consecutive-plays (new-state t1)) 2) (str "Home team " t1 " should not play more than 2 consecutive games"))
            (is (<= (:consecutive-plays (new-state t2)) 2) (str "Away team " t2 " should not play more than 2 consecutive games"))
            (doseq [team teams]
              (when-not (or (= t1 team) (= t2 team))
                (is (<= (:consecutive-rests (new-state team)) 2) (str "Team " team " should not rest for more than 2 consecutive games"))))
            (recur (rest remaining-schedule) (conj previous-matches current-match) new-state)))))))