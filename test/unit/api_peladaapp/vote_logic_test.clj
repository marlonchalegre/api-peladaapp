(ns api-peladaapp.vote-logic-test
  (:require
   [api-peladaapp.logic.vote :as vote.logic]
   [clojure.test :refer [deftest is testing]])
  (:import
   [java.time Duration Instant]))

(deftest test-validate-vote
  (let [uuid-1 (parse-uuid "00000000-0000-0000-0000-000000000001")
        uuid-2 (parse-uuid "00000000-0000-0000-0000-000000000002")]
    (testing "Valid vote should pass"
      (let [vote {:voter-id uuid-1 :target-id uuid-2 :stars 4}]
        (is (= vote (vote.logic/validate-vote vote)))))

    (testing "Self vote should throw"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Self vote not allowed"
                            (vote.logic/validate-vote {:voter-id uuid-1 :target-id uuid-1 :stars 5}))))

    (testing "Invalid stars should throw"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid vote stars"
                            (vote.logic/validate-vote {:voter-id uuid-1 :target-id uuid-2 :stars 6})))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid vote stars"
                            (vote.logic/validate-vote {:voter-id uuid-1 :target-id uuid-2 :stars 0}))))))

(deftest test-validate-voting-eligibility
  (testing "Open pelada should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Pelada must be closed to vote"
                          (vote.logic/validate-voting-eligibility {:status "open"}))))

  (testing "Closed pelada with no closed_at should throw"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Pelada has no closed_at timestamp"
                          (vote.logic/validate-voting-eligibility {:status "closed"}))))

  (testing "Closed pelada within 24h window should pass"
    (let [now (Instant/now)
          two-hours-ago (.minus now (Duration/ofHours 2))
          pelada {:status "closed" :closed-at two-hours-ago}]
      (is (= pelada (vote.logic/validate-voting-eligibility pelada)))))

  (testing "Closed pelada after 24h window should throw"
    (let [now (Instant/now)
          twenty-five-hours-ago (.minus now (Duration/ofHours 25))
          pelada {:status "closed" :closed-at twenty-five-hours-ago}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Voting window closed"
                            (vote.logic/validate-voting-eligibility pelada))))))

(deftest test-voting-open?
  (testing "Open pelada should return false"
    (is (false? (vote.logic/voting-open? {:status "open"}))))

  (testing "Closed pelada within window should return true"
    (let [now (Instant/now)
          two-hours-ago (.minus now (Duration/ofHours 2))
          pelada {:status "closed" :closed-at two-hours-ago}]
      (is (true? (vote.logic/voting-open? pelada)))))

  (testing "Closed pelada after window should return false"
    (let [now (Instant/now)
          twenty-five-hours-ago (.minus now (Duration/ofHours 25))
          pelada {:status "closed" :closed-at twenty-five-hours-ago}]
      (is (false? (vote.logic/voting-open? pelada))))))
