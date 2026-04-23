(ns api-peladaapp.waha-test
  (:require
   [api-peladaapp.logic.waha :as waha]
   [clojure.test :refer [deftest is testing]]))

(deftest normalize-phone-test
  (testing "Brazilian numbers (DDD 11-28) - keep 9th digit"
    (is (= "5511999999999@c.us" (waha/normalize-phone "5511999999999")))
    (is (= "5521988887777@c.us" (waha/normalize-phone "+55 (21) 98888-7777"))))

  (testing "Brazilian numbers (DDD > 28) - remove 9th digit"
    (is (= "554188887777@c.us" (waha/normalize-phone "5541988887777")))
    (is (= "554188887777@c.us" (waha/normalize-phone "+55 (41) 98888-7777"))))

  (testing "Brazilian numbers (DDD > 28) - keep if 8 digits"
    (is (= "554188887777@c.us" (waha/normalize-phone "554188887777"))))

  (testing "Non-Brazilian numbers"
    (is (= "12025550123@c.us" (waha/normalize-phone "+1 202-555-0123"))))

  (testing "Empty or nil phone"
    (is (nil? (waha/normalize-phone "")))
    (is (nil? (waha/normalize-phone nil)))))
