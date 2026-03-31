(ns api-peladaapp.logic.schedule-fixed-test
  (:require
   [api-peladaapp.logic.schedule :as schedule]
   [clojure.test :refer [deftest is testing]]))

(deftest schedule-fixed-combinations-test
  (testing "4 teams, 5 matches per team follows user pattern"
    (let [teams [1 2 3 4]
          matches (vec (schedule/schedule-matches-with-limit teams 5))]
      (is (= 10 (count matches)))
      ;; R1: T1xT2, T3xT4
      (is (= (set [1 2]) (set [(:home (nth matches 0)) (:away (nth matches 0))])))
      (is (= (set [3 4]) (set [(:home (nth matches 1)) (:away (nth matches 1))])))

      ;; R2: T1xT3, T4xT2
      (is (= (set [1 3]) (set [(:home (nth matches 2)) (:away (nth matches 2))])))
      (is (= (set [4 2]) (set [(:home (nth matches 3)) (:away (nth matches 3))])))))

  (testing "3 teams, 6 matches per team follows PDF pattern"
    (let [teams [1 2 3]
          matches (vec (schedule/schedule-matches-with-limit teams 6))]
      (is (= 9 (count matches)))
      ;; R1: 1x2
      (is (= (set [1 2]) (set [(:home (nth matches 0)) (:away (nth matches 0))])))
      ;; R2: 3x1
      (is (= (set [3 1]) (set [(:home (nth matches 1)) (:away (nth matches 1))])))
      ;; R3: 2x3
      (is (= (set [2 3]) (set [(:home (nth matches 2)) (:away (nth matches 2))]))))))

(deftest schedule-5-6-teams-berger-test
  (testing "6 teams follows Berger table from PDF"
    (let [teams [1 2 3 4 5 6]
          matches (vec (schedule/schedule-matches-with-limit teams 5))]
      (is (= 15 (count matches)))
      ;; R1: 2x1, 3x5, 4x6
      (let [r1 (set [(set [(:home (nth matches 0)) (:away (nth matches 0))])
                     (set [(:home (nth matches 1)) (:away (nth matches 1))])
                     (set [(:home (nth matches 2)) (:away (nth matches 2))])])]
        (is (= (set [(set [1 2]) (set [3 5]) (set [4 6])]) r1))))))
