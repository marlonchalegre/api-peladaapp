(ns api-peladaapp.logic.ils-schedule-test
  (:require
   [api-peladaapp.logic.ils-schedule :as ils]
   [clojure.test :refer [deftest is testing]]))

(deftest test-berger-schedule-scenarios
  (testing "n < 2 returns empty list"
    (is (= [] (ils/schedule-matches-ils ["team1"] 3)))
    (is (= [] (ils/schedule-matches-ils [] 3))))

  (testing "n = 2"
    (let [matches (ils/schedule-matches-ils ["t1" "t2"] 1)]
      (is (= [{:home "t1" :away "t2"}] matches))))

  (testing "n = 3"
    (let [matches (ils/schedule-matches-ils ["t1" "t2" "t3"] 2)]
      (is (= 3 (count matches)))
      (is (every? (fn [{:keys [home away]}] (not= home away)) matches))))

  (testing "n = 4"
    (let [matches (ils/schedule-matches-ils ["t1" "t2" "t3" "t4"] 3)]
      (is (= 6 (count matches)))))

  (testing "n = 5 (checks the :bye branch)"
    (let [matches (ils/schedule-matches-ils ["t1" "t2" "t3" "t4" "t5"] 4)]
      (is (pos? (count matches)))
      (is (not (some (fn [{:keys [home away]}] (or (= home :bye) (= away :bye))) matches)))))

  (testing "n = 8 with mocked berger-schedule to trigger ILS metaheuristic loop"
    (with-redefs [ils/berger-schedule (fn [_ _] [{:home "t1" :away "t2"}
                                                 {:home "t3" :away "t4"}
                                                 {:home "t5" :away "t6"}
                                                 {:home "t7" :away "t8"}])]
      (let [teams ["t1" "t2" "t3" "t4" "t5" "t6" "t7" "t8"]
            matches (ils/schedule-matches-ils teams 4)]
        (is (pos? (count matches))))))

  (testing "ils else branch in berger-schedule"
    (is (= [] (ils/berger-schedule ["t1" "t2" "t3" "t4" "t5" "t6" "t7"] 3))))

  (testing "runs ILS loop when initial cost is positive"
    (let [teams ["t1" "t2" "t3" "t4" "t5" "t6" "t7" "t8"]]
      (with-redefs [ils/berger-schedule (fn [_ _] [{:home "t1" :away "t2"}])
                    ils/cost (fn [m _ _]
                               (if (= m [{:home "t1" :away "t2"}])
                                 10
                                 0))]
        (let [matches (ils/schedule-matches-ils teams 4)]
          (is (pos? (count matches))))))))

(deftest test-private-helpers
  (testing "swap-teams-in-schedule"
    (let [swap-teams-in-schedule #'ils/swap-teams-in-schedule
          matches [{:home "t1" :away "t2"} {:home "t3" :away "t1"}]]
      (is (= [{:home "t2" :away "t1"} {:home "t3" :away "t2"}]
             (swap-teams-in-schedule matches "t1" "t2")))))

  (testing "flip-match"
    (let [flip-match #'ils/flip-match
          matches [{:home "t1" :away "t2"} {:home "t3" :away "t4"}]]
      (is (= [{:home "t2" :away "t1"} {:home "t3" :away "t4"}]
             (flip-match matches 0)))))

  (testing "swap-matches"
    (let [swap-matches #'ils/swap-matches
          matches [{:home "t1" :away "t2"} {:home "t3" :away "t4"}]]
      (is (= [{:home "t3" :away "t4"} {:home "t1" :away "t2"}]
             (swap-matches matches 0 1)))))

  (testing "perturb"
    (let [perturb #'ils/perturb
          matches [{:home "t1" :away "t2"} {:home "t3" :away "t4"}]]
      ;; perturb returns perturbed matches (swapped and flipped)
      (is (= 2 (count (perturb matches))))
      ;; empty list of matches perturbed should return empty list
      (is (= [] (perturb [])))))

  (testing "local-search"
    (let [local-search #'ils/local-search
          matches [{:home "t1" :away "t2"} {:home "t3" :away "t4"}]]
      (is (pos? (count (local-search matches ["t1" "t2" "t3" "t4"] 2)))))))
