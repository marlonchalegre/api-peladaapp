(ns api-peladaapp.schedule-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-peladaapp.logic.schedule :as schedule]))

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
  (testing "even teams required"
    (is (thrown? AssertionError (schedule/schedule-matches [1 2 3])))))
