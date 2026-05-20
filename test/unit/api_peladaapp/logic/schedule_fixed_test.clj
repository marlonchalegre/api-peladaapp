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

(deftest schedule-uuid-preservation-test
  (testing "preserves the order of UUIDs passed from caller without sorting them by UUID string/value"
    (let [u1 (java.util.UUID/fromString "dddddddd-dddd-dddd-dddd-dddddddddddd")
          u2 (java.util.UUID/fromString "cccccccc-cccc-cccc-cccc-cccccccccccc")
          u3 (java.util.UUID/fromString "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
          u4 (java.util.UUID/fromString "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
          teams [u1 u2 u3 u4]
          matches (vec (schedule/schedule-matches-with-limit teams 5))]
      (is (= 10 (count matches)))
      ;; Check R1: T1 vs T2, T3 vs T4 (based on original order [u1 u2 u3 u4])
      (is (= (set [u1 u2]) (set [(:home (nth matches 0)) (:away (nth matches 0))])))
      (is (= (set [u3 u4]) (set [(:home (nth matches 1)) (:away (nth matches 1))]))))))

