(ns api-peladaapp.logic.match-test
  (:require
   [api-peladaapp.logic.match :as match.logic]
   [clojure.test :refer [deftest is testing]]))

(deftest test-ensure-non-negative
  (testing "Does not throw when value is nil, zero, or positive"
    (is (nil? (#'match.logic/ensure-non-negative nil :home-score)))
    (is (nil? (#'match.logic/ensure-non-negative 0 :home-score)))
    (is (nil? (#'match.logic/ensure-non-negative 5 :home-score))))

  (testing "Throws when value is negative"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Negative score not allowed"
         (#'match.logic/ensure-non-negative -1 :home-score)))))

(deftest test-build-score-update
  (testing "Builds update map with home-score only"
    (is (= {:home-score 3} (match.logic/build-score-update {:home-score 3}))))

  (testing "Builds update map with away-score only"
    (is (= {:away-score 2} (match.logic/build-score-update {:away-score 2}))))

  (testing "Builds update map with status only"
    (is (= {:status "finished"} (match.logic/build-score-update {:status "finished"}))))

  (testing "Builds update map with all fields"
    (is (= {:home-score 3 :away-score 2 :status "finished"}
           (match.logic/build-score-update {:home-score 3 :away-score 2 :status "finished"}))))

  (testing "Throws when negative home score is provided"
    (is (thrown? clojure.lang.ExceptionInfo
                 (match.logic/build-score-update {:home-score -2}))))

  (testing "Throws when negative away score is provided"
    (is (thrown? clojure.lang.ExceptionInfo
                 (match.logic/build-score-update {:away-score -1}))))

  (testing "Throws when payload is empty (no valid fields)"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Missing score fields"
         (match.logic/build-score-update {})))))
