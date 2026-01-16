(ns api-peladaapp.score-logic-test
  (:require
   [api-peladaapp.logic.score :as score.logic]
   [clojure.test :refer [deftest is testing]]
   [next.jdbc :as jdbc]))

(deftest test-get-normalized-scores
  (testing "Empty player-ids returns empty map"
    (is (= {} (score.logic/get-normalized-scores [] {}))))

  (testing "Returns default 5.0 for player with no votes"
    (with-redefs [jdbc/execute! (fn [_ _ _] [])] ;; DB returns empty
      (let [scores (score.logic/get-normalized-scores [1] {})]
        (is (= 5.0 (get scores 1))))))

  (testing "Returns normalized score (x2) for player with votes"
    ;; Mock DB returning average score 4.5
    (with-redefs [jdbc/execute! (fn [_ _ _] [{:target_id 1 :score 4.5}])]
      (let [scores (score.logic/get-normalized-scores [1] {})]
        (is (= 9.0 (get scores 1))))))

  (testing "Returns mixed results: existing scores normalized, missing ones defaulted"
    ;; Mock DB returning score for player 1 only
    (with-redefs [jdbc/execute! (fn [_ _ _] [{:target_id 1 :score 3.5}])]
      (let [scores (score.logic/get-normalized-scores [1 2] {})]
        (is (= 7.0 (get scores 1)))   ;; 3.5 * 2
        (is (= 5.0 (get scores 2))))))) ;; Default
