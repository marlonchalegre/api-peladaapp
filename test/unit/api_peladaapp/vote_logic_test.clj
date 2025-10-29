(ns api-peladaapp.vote-logic-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-peladaapp.logic.vote :as vote.logic])
  (:import [java.time Instant Duration]))

(deftest test-ensure-not-self-vote
  (testing "Self vote should throw exception"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Self vote not allowed"
          (vote.logic/ensure-not-self-vote 1 1))))
  
  (testing "Different voter and target should pass"
    (is (nil? (vote.logic/ensure-not-self-vote 1 2)))))

(deftest test-ensure-valid-stars
  (testing "Stars below 1 should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid vote stars"
          (vote.logic/ensure-valid-stars 0))))
  
  (testing "Stars above 5 should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid vote stars"
          (vote.logic/ensure-valid-stars 6))))
  
  (testing "Valid stars 1-5 should pass"
    (doseq [stars [1 2 3 4 5]]
      (is (nil? (vote.logic/ensure-valid-stars stars))))))

(deftest test-ensure-pelada-closed
  (testing "Open pelada should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Pelada must be closed to vote"
          (vote.logic/ensure-pelada-closed {:status "open"}))))
  
  (testing "Running pelada should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Pelada must be closed to vote"
          (vote.logic/ensure-pelada-closed {:status "running"}))))
  
  (testing "Closed pelada should pass"
    (is (nil? (vote.logic/ensure-pelada-closed {:status "closed"})))))

(deftest test-ensure-voting-window-open
  (testing "Missing closed_at should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot determine voting window"
          (vote.logic/ensure-voting-window-open {:status "closed"}))))
  
  (testing "Within 24 hours should pass"
    (let [now (Instant/now)
          two-hours-ago (.minus now (Duration/ofHours 2))
          pelada {:status "closed" :closed_at two-hours-ago}]
      (is (nil? (vote.logic/ensure-voting-window-open pelada)))))
  
  (testing "Exactly 24 hours should pass"
    (let [now (Instant/now)
          exactly-24h-ago (.minus now (Duration/ofHours 24))
          pelada {:status "closed" :closed_at exactly-24h-ago}]
      (is (nil? (vote.logic/ensure-voting-window-open pelada)))))
  
  (testing "After 24 hours should throw"
    (let [now (Instant/now)
          twenty-five-hours-ago (.minus now (Duration/ofHours 25))
          pelada {:status "closed" :closed_at twenty-five-hours-ago}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Voting window closed"
            (vote.logic/ensure-voting-window-open pelada))))))

(deftest test-validate-vote
  (testing "Valid vote should pass"
    (let [vote {:voter_id 1 :target_id 2 :stars 4}]
      (is (= vote (vote.logic/validate-vote vote)))))
  
  (testing "Self vote should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Self vote not allowed"
          (vote.logic/validate-vote {:voter_id 1 :target_id 1 :stars 4}))))
  
  (testing "Invalid stars should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid vote stars"
          (vote.logic/validate-vote {:voter_id 1 :target_id 2 :stars 0})))))

(deftest test-validate-voting-eligibility
  (testing "Open pelada should fail"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Pelada must be closed to vote"
          (vote.logic/validate-voting-eligibility {:status "open"}))))
  
  (testing "Closed pelada without closed_at should fail"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot determine voting window"
          (vote.logic/validate-voting-eligibility {:status "closed"}))))
  
  (testing "Closed pelada within 24h should pass"
    (let [now (Instant/now)
          two-hours-ago (.minus now (Duration/ofHours 2))
          pelada {:status "closed" :closed_at two-hours-ago}]
      (is (= pelada (vote.logic/validate-voting-eligibility pelada)))))
  
  (testing "Closed pelada after 24h should fail"
    (let [now (Instant/now)
          twenty-five-hours-ago (.minus now (Duration/ofHours 25))
          pelada {:status "closed" :closed_at twenty-five-hours-ago}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Voting window closed"
            (vote.logic/validate-voting-eligibility pelada))))))

(deftest test-normalized-score
  (testing "Empty votes should return 0.0"
    (let [result (vote.logic/normalized-score 1 [])]
      (is (= 1 (:player_id result)))
      (is (= 0.0 (:score result)))))
  
  (testing "Single vote of 5 stars should return 10.0"
    (let [votes [{:stars 5}]
          result (vote.logic/normalized-score 1 votes)]
      (is (= 1 (:player_id result)))
      (is (= 10.0 (:score result)))))
  
  (testing "Average of 3 and 5 stars (4.0) should return 8.0"
    (let [votes [{:stars 3} {:stars 5}]
          result (vote.logic/normalized-score 1 votes)]
      (is (= 1 (:player_id result)))
      (is (= 8.0 (:score result)))))
  
  (testing "Average of 1, 2, 3, 4, 5 stars (3.0) should return 6.0"
    (let [votes [{:stars 1} {:stars 2} {:stars 3} {:stars 4} {:stars 5}]
          result (vote.logic/normalized-score 1 votes)]
      (is (= 1 (:player_id result)))
      (is (= 6.0 (:score result))))))
