(ns api-peladaapp.score-logic-test
  (:require
   [api-peladaapp.logic.score :as score.logic]
   [clojure.test :refer [deftest is testing]]
   [next.jdbc :as jdbc]))

(deftest test-get-normalized-scores
  (let [uuid-1 (parse-uuid "00000000-0000-0000-0000-000000000001")
        uuid-2 (parse-uuid "00000000-0000-0000-0000-000000000002")]
    (testing "Empty player-ids returns empty map"
      (is (= {} (score.logic/get-normalized-scores [] {}))))

    (testing "Returns default 5.0 for player with no record in DB"
      (with-redefs [jdbc/execute! (fn [_ _ _] [])] ;; DB returns empty
        (let [scores (score.logic/get-normalized-scores [uuid-1] {})]
          (is (= 5.0 (get scores uuid-1))))))

    (testing "Returns stored grade from DB"
      ;; Mock DB returning grade 9.0
      (with-redefs [jdbc/execute! (fn [_ _ _] [{:id uuid-1 :grade 9.0}])]
        (let [scores (score.logic/get-normalized-scores [uuid-1] {})]
          (is (= 9.0 (get scores uuid-1))))))

    (testing "Returns mixed results: existing grades from DB, missing ones defaulted"
      ;; Mock DB returning grade for player 1 only
      (with-redefs [jdbc/execute! (fn [_ _ _] [{:id uuid-1 :grade 7.5}])]
        (let [scores (score.logic/get-normalized-scores [uuid-1 uuid-2] {})]
          (is (= 7.5 (get scores uuid-1)))
          (is (= 5.0 (get scores uuid-2))))))

    (testing "Defaults to 5.0 if grade is nil in DB"
      (with-redefs [jdbc/execute! (fn [_ _ _] [{:id uuid-1 :grade nil}])]
        (let [scores (score.logic/get-normalized-scores [uuid-1] {})]
          (is (= 5.0 (get scores uuid-1))))))))
