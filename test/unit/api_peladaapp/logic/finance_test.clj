(ns api-peladaapp.logic.finance-test
  (:require
   [api-peladaapp.logic.finance :as logic.finance]
   [clojure.test :refer [deftest is testing]]))

(deftest calculate-monthly-fine-test
  (testing "should not apply fine on or before cut-off day"
    (is (= 0.0 (logic.finance/calculate-monthly-fine 2026 5 "2026-05-01" 9.0 5)))
    (is (= 0.0 (logic.finance/calculate-monthly-fine 2026 5 "2026-05-05" 9.0 5))))

  (testing "should apply fine after cut-off day"
    (is (= 9.0 (logic.finance/calculate-monthly-fine 2026 5 "2026-05-06" 9.0 5)))
    (is (= 15.0 (logic.finance/calculate-monthly-fine 2026 5 "2026-05-10" 15.0 5)))
    (is (= 9.0 (logic.finance/calculate-monthly-fine 2026 5 "2026-05-11" 9.0 10))))

  (testing "should handle paying early for next month"
    (is (= 0.0 (logic.finance/calculate-monthly-fine 2026 6 "2026-05-30" 9.0 5))))

  (testing "should handle paying late for previous month"
    (is (= 9.0 (logic.finance/calculate-monthly-fine 2026 4 "2026-05-01" 9.0 5)))))
