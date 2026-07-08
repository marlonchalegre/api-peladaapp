(ns api-peladaapp.logic.grade-test
  (:require
   [api-peladaapp.logic.grade :as grade.logic]
   [clojure.test :refer [deftest is testing]]))

(deftest test-performance-from-stars
  (testing "Defaults stars to 3.0 when nil"
    (is (= 7.0 (grade.logic/performance-from-stars nil))))

  (testing "x <= 1.0 returns 1.0"
    (is (= 1.0 (grade.logic/performance-from-stars 0.5)))
    (is (= 1.0 (grade.logic/performance-from-stars 1.0))))

  (testing "1.0 < x <= 2.0 uses linear interpolation"
    (is (= 2.5 (grade.logic/performance-from-stars 1.5)))
    (is (= 4.0 (grade.logic/performance-from-stars 2.0))))

  (testing "2.0 < x <= 3.0 uses linear interpolation"
    (is (= 5.5 (grade.logic/performance-from-stars 2.5)))
    (is (= 7.0 (grade.logic/performance-from-stars 3.0))))

  (testing "3.0 < x <= 4.0 uses linear interpolation"
    (is (= 8.0 (grade.logic/performance-from-stars 3.5)))
    (is (= 9.0 (grade.logic/performance-from-stars 4.0))))

  (testing "4.0 < x <= 5.0 uses linear interpolation"
    (is (= 9.5 (grade.logic/performance-from-stars 4.5)))
    (is (= 10.0 (grade.logic/performance-from-stars 5.0))))

  (testing "x > 5.0 returns 10.0 (else branch)"
    (is (= 10.0 (grade.logic/performance-from-stars 5.5)))))

(deftest test-calculate-new-grade
  (testing "Defaults old-grade to 5.0 when nil"
    ;; Using default k = 0.2
    ;; 5.0 * 0.8 + 8.0 * 0.2 = 4.0 + 1.6 = 5.6
    (is (= 5.6 (grade.logic/calculate-new-grade nil 8.0))))

  (testing "Uses current old-grade and k default when old-grade is provided"
    ;; 6.0 * 0.8 + 8.0 * 0.2 = 4.8 + 1.6 = 6.4
    (is (= 6.4 (grade.logic/calculate-new-grade 6.0 8.0))))

  (testing "Uses custom k when provided"
    ;; 6.0 * (1 - 0.5) + 8.0 * 0.5 = 3.0 + 4.0 = 7.0
    (is (= 7.0 (grade.logic/calculate-new-grade 6.0 8.0 0.5)))))
