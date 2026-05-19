(ns api-peladaapp.randomize-test
  (:require
   [api-peladaapp.logic.randomize :as randomize]
   [clojure.test :refer [deftest is testing]]))

;; Accessing private functions for testing purposes
;; In a real project, we might expose these or test through the public API
;; but here we want to verify the logic directly.

(deftest randomization-balance-test
  (testing "Balanced distribution of players with different grades"
    (let [players [{:id 1 :position "Striker" :grade 5}
                   {:id 2 :position "Striker" :grade 1}
                   {:id 3 :position "Striker" :grade 4}
                   {:id 4 :position "Striker" :grade 2}
                   {:id 5 :position "Defender" :grade 5}
                   {:id 6 :position "Defender" :grade 1}
                   {:id 7 :position "Defender" :grade 4}
                   {:id 8 :position "Defender" :grade 2}
                   {:id 9 :position "Midfielder" :grade 5}
                   {:id 10 :position "Midfielder" :grade 1}]
          num-teams 2
          players-per-team 5
          initial-states (mapv (fn [i] {:id i :current-score 0 :current-count 0 :max-players players-per-team :positions {}}) (range num-teams))

          ;; Using the same logic as in randomize-teams! but isolated
          best-candidate (->> (repeatedly 50 (fn []
                                               (let [sorted (#'randomize/sort-players-for-balance (shuffle players) num-teams)]
                                                 (#'randomize/generate-assignment-candidate sorted initial-states))))
                              (apply min-key :imbalance))

          assignments (:assignments best-candidate)
          final-scores (->> assignments
                            (group-by :team_id)
                            (map (fn [[_ as]]
                                   (reduce + (map (fn [a] (or (:grade (first (filter #(= (:id %) (:player_id a)) players))) 0)) as)))))]

      (is (= 10 (count assignments)) "All players should be assigned")
      (is (every? #(= 5 %) (map count (vals (group-by :team_id assignments)))) "Teams should be full")

      ;; With Best-of-50 and this data set, imbalance should be very low (usually 0 or 1)
      (let [diff (Math/abs (- (first final-scores) (second final-scores)))]
        (is (<= diff 2) (str "Team imbalance too high: " diff " (scores: " final-scores ")"))))))

(deftest position-distribution-test
  (testing "Position balance is maintained"
    (let [players [{:id 1 :position "Striker" :grade 5}
                   {:id 2 :position "Striker" :grade 5}
                   {:id 3 :position "Defender" :grade 5}
                   {:id 4 :position "Defender" :grade 5}]
          num-teams 2
          players-per-team 2
          initial-states (mapv (fn [i] {:id i :current-score 0 :current-count 0 :max-players players-per-team :positions {}}) (range num-teams))

          best-candidate (->> (repeatedly 20 (fn []
                                               (let [sorted (#'randomize/sort-players-for-balance (shuffle players) num-teams)]
                                                 (#'randomize/generate-assignment-candidate sorted initial-states))))
                              (apply min-key :imbalance))

          assignments (:assignments best-candidate)
          teams-players (group-by :team_id assignments)
          team-positions (map (fn [[_ as]]
                                (map #(:position (first (filter (fn [p] (= (:id p) (:player_id %))) players))) as))
                              teams-players)]

      (is (every? (fn [ps] (some #{"Striker"} ps)) team-positions) "Each team should have at least one striker")
      (is (every? (fn [ps] (some #{"Defender"} ps)) team-positions) "Each team should have at least one defender"))))
