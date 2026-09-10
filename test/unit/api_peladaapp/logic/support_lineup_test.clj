(ns api-peladaapp.logic.support-lineup-test
  (:require
   [api-peladaapp.logic.support-lineup :as support-lineup]
   [clojure.test :refer [deftest is testing]]))

(deftest test-eligible-resting-players
  (let [t1 (java.util.UUID/randomUUID)
        t2 (java.util.UUID/randomUUID)
        t3 (java.util.UUID/randomUUID)
        p1 (java.util.UUID/randomUUID)
        p2 (java.util.UUID/randomUUID)
        p3 (java.util.UUID/randomUUID)
        p4 (java.util.UUID/randomUUID)
        team-players [{:team-id t1 :player-id p1}
                      {:team-id t2 :player-id p2}
                      {:team-id t3 :player-id p3}
                      {:team-id t3 :player-id p4}]]
    (testing "Picks players only from resting teams"
      (let [candidates (support-lineup/eligible-resting-players [t3] team-players [])]
        (is (= #{p3 p4} (set candidates)))))

    (testing "Filters out players who are not confirmed"
      (let [candidates (support-lineup/eligible-resting-players [t3] team-players [p3])]
        (is (= [p3] candidates))))))

(deftest test-pick-support-pair-fairness
  (let [p1 (java.util.UUID/randomUUID)
        p2 (java.util.UUID/randomUUID)
        p3 (java.util.UUID/randomUUID)
        candidates [p1 p2 p3]]
    (testing "Picks two candidates with 0 duties first when one candidate already has 1 duty"
      (let [duty-counts {p1 1 p2 0 p3 0}
            [c s] (support-lineup/pick-support-pair candidates duty-counts)]
        (is (not= c s))
        (is (= #{p2 p3} #{c s}))))

    (testing "Allows repeating when all candidates have the same duty count"
      (let [duty-counts {p1 1 p2 1 p3 1}
            [c s] (support-lineup/pick-support-pair candidates duty-counts)]
        (is (not= c s))
        (is (contains? #{p1 p2 p3} c))
        (is (contains? #{p1 p2 p3} s))))

    (testing "Handles empty and single candidate gracefully"
      (is (= [nil nil] (support-lineup/pick-support-pair [] {})))
      (is (= [p1 nil] (support-lineup/pick-support-pair [p1] {}))))))

(deftest test-generate-support-lineups
  (let [t1 (java.util.UUID/randomUUID)
        t2 (java.util.UUID/randomUUID)
        t3 (java.util.UUID/randomUUID)
        p1 (java.util.UUID/randomUUID)
        p2 (java.util.UUID/randomUUID)
        p3 (java.util.UUID/randomUUID)
        p4 (java.util.UUID/randomUUID)
        p5 (java.util.UUID/randomUUID)
        p6 (java.util.UUID/randomUUID)
        team-players [{:team-id t1 :player-id p1}
                      {:team-id t1 :player-id p2}
                      {:team-id t2 :player-id p3}
                      {:team-id t2 :player-id p4}
                      {:team-id t3 :player-id p5}
                      {:team-id t3 :player-id p6}]
        matches [{:id (java.util.UUID/randomUUID) :home-team-id t1 :away-team-id t2 :sequence 1}
                 {:id (java.util.UUID/randomUUID) :home-team-id t2 :away-team-id t3 :sequence 2}
                 {:id (java.util.UUID/randomUUID) :home-team-id t3 :away-team-id t1 :sequence 3}]]
    (testing "Assigns support pair from resting teams to each match"
      (let [results (support-lineup/generate-support-lineups matches [t1 t2 t3] team-players [])]
        (is (= 3 (count results)))
        ;; Match 1: t3 is resting, so p5 and p6 should be assigned
        (let [m1 (first results)]
          (is (= #{p5 p6} #{(:support-camera-player-id m1) (:support-stats-player-id m1)})))
        ;; Match 2: t1 is resting, so p1 and p2 should be assigned
        (let [m2 (second results)]
          (is (= #{p1 p2} #{(:support-camera-player-id m2) (:support-stats-player-id m2)})))
        ;; Match 3: t2 is resting, so p3 and p4 should be assigned
        (let [m3 (nth results 2)]
          (is (= #{p3 p4} #{(:support-camera-player-id m3) (:support-stats-player-id m3)})))))))

(deftest test-reroll-single-match
  (let [t1 (java.util.UUID/randomUUID)
        t2 (java.util.UUID/randomUUID)
        t3 (java.util.UUID/randomUUID)
        p1 (java.util.UUID/randomUUID)
        p2 (java.util.UUID/randomUUID)
        p3 (java.util.UUID/randomUUID)
        team-players [{:team-id t1 :player-id p1}
                      {:team-id t2 :player-id p2}
                      {:team-id t3 :player-id p3}]
        target-match {:id (java.util.UUID/randomUUID) :home-team-id t1 :away-team-id t2}
        other-matches [{:id (java.util.UUID/randomUUID) :home-team-id t2 :away-team-id t3
                        :support-camera-player-id p1 :support-stats-player-id nil}]]
    (testing "Re-rolls using history from other matches"
      (let [result (support-lineup/reroll-single-match target-match other-matches [t1 t2 t3] team-players [])]
        (is (= p3 (:support-camera-player-id result)))))))

(deftest test-two-teams-pelada-edge-case
  (let [t1 (java.util.UUID/randomUUID)
        t2 (java.util.UUID/randomUUID)
        p1 (java.util.UUID/randomUUID)
        p2 (java.util.UUID/randomUUID)
        team-players [{:team-id t1 :player-id p1}
                      {:team-id t2 :player-id p2}]
        matches [{:id (java.util.UUID/randomUUID) :home-team-id t1 :away-team-id t2 :sequence 1}]]
    (testing "When pelada only has 2 teams (no resting teams), support is gracefully nil"
      (let [results (support-lineup/generate-support-lineups matches [t1 t2] team-players [])]
        (is (= 1 (count results)))
        (is (nil? (:support-camera-player-id (first results))))
        (is (nil? (:support-stats-player-id (first results))))))))

(deftest test-single-resting-player-edge-case
  (let [t1 (java.util.UUID/randomUUID)
        t2 (java.util.UUID/randomUUID)
        t3 (java.util.UUID/randomUUID)
        p1 (java.util.UUID/randomUUID)
        team-players [{:team-id t3 :player-id p1}]
        matches [{:id (java.util.UUID/randomUUID) :home-team-id t1 :away-team-id t2 :sequence 1}]]
    (testing "When only 1 resting player is available, camera is assigned and stats is nil"
      (let [results (support-lineup/generate-support-lineups matches [t1 t2 t3] team-players [])]
        (is (= p1 (:support-camera-player-id (first results))))
        (is (nil? (:support-stats-player-id (first results))))))))

(deftest test-fallback-when-no-resting-players-confirmed
  (let [t1 (java.util.UUID/randomUUID)
        t2 (java.util.UUID/randomUUID)
        t3 (java.util.UUID/randomUUID)
        p1 (java.util.UUID/randomUUID)
        p2 (java.util.UUID/randomUUID)
        p3 (java.util.UUID/randomUUID)
        team-players [{:team-id t1 :player-id p1}
                      {:team-id t2 :player-id p2}
                      {:team-id t3 :player-id p3}]
        matches [{:id (java.util.UUID/randomUUID) :home-team-id t1 :away-team-id t2 :sequence 1}]]
    (testing "Falls back to resting team members when confirmed list has players, but none resting"
      ;; Confirmed list only has p1 and p2 (who are playing in match 1). Resting player p3 was not in attendance list.
      (let [results (support-lineup/generate-support-lineups matches [t1 t2 t3] team-players [p1 p2])]
        (is (= p3 (:support-camera-player-id (first results))))))))

(deftest test-calculate-duty-stats
  (let [p1 (java.util.UUID/randomUUID)
        p2 (java.util.UUID/randomUUID)
        matches [{:support-camera-player-id p1 :support-stats-player-id p2}
                 {:support-camera-player-id p1 :support-stats-player-id nil}]
        stats (support-lineup/calculate-duty-stats matches)]
    (is (= 2 (get-in stats [p1 :camera-count])))
    (is (= 2 (get-in stats [p1 :total])))
    (is (= 1 (get-in stats [p2 :stats-count])))
    (is (= 1 (get-in stats [p2 :total])))))

