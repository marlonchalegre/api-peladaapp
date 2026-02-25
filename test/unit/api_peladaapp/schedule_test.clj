(ns api-peladaapp.schedule-test
  (:require
   [api-peladaapp.logic.schedule :as schedule]
   [clojure.test :refer [deftest is testing]]))

(deftest schedule-generates-balanced-sequence
  (testing "basic round-robin generation"
    (let [teams [1 2 3 4]
          matches (vec (schedule/schedule-matches teams))]
      (is (pos? (count matches)))
      ;; each match has two distinct teams
      (is (every? (fn [{:keys [home away]}] (and home away (not= home away))) matches))
      ;; ensure each team appears reasonable times (round-robin each team plays 3 times in 4 teams)
      (let [freqs (frequencies (mapcat (fn [{:keys [home away]}] [home away]) matches))]
        (doseq [t teams]
          (is (= 3 (get freqs t 0)))))))

  (testing "starting matches for 4 teams"
    (let [teams [1 2 3 4]
          matches (vec (schedule/schedule-matches teams))]
      ;; First match should be 1x2 (or 2x1)
      (is (or (and (= 1 (get-in matches [0 :home])) (= 2 (get-in matches [0 :away])))
              (and (= 2 (get-in matches [0 :home])) (= 1 (get-in matches [0 :away])))))
      ;; Second match should be 3x4 (or 4x3)
      (is (or (and (= 3 (get-in matches [1 :home])) (= 4 (get-in matches [1 :away])))
              (and (= 4 (get-in matches [1 :home])) (= 3 (get-in matches [1 :away]))))))))
