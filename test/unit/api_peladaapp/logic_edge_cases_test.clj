(ns api-peladaapp.logic-edge-cases-test
  (:require
   [api-peladaapp.adapters.user :as adapter.user]
   [api-peladaapp.logic.schedule :as sch]
   [api-peladaapp.logic.vote :as vote.logic]
   [clojure.test :refer [deftest is testing]])
  (:import
   [java.time Duration Instant]))

(deftest scheduler-edge-cases
  (testing "2 teams, 1 match per team"
    (let [res (sch/schedule-matches-with-limit [1 2] 1)]
      (is (= 1 (count res)))))

  (testing "3 teams, 2 matches per team (total 3 matches)"
    (let [res (sch/schedule-matches-with-limit [1 2 3] 2)]
      (is (= 3 (count res)))))

  (testing "Large number of matches"
    (let [res (sch/schedule-matches-with-limit [1 2 3 4] 10)]
      (is (= 20 (count res)))))

  (testing "Invalid input: 1 team"
    (is (empty? (sch/schedule-matches-with-limit [1] 5))))

  (testing "Invalid input: 0 teams"
    (is (empty? (sch/schedule-matches-with-limit [] 5)))))

(deftest vote-logic-edge-cases
  (testing "voting-open? with nil status"
    (is (false? (vote.logic/voting-open? {:status nil}))))

  (testing "voting-open? with nil closed-at"
    (is (false? (vote.logic/voting-open? {:status "closed" :closed-at nil}))))

  (testing "voting-open? with future closed-at (should not happen but test logic)"
    (let [future-time (str (.plus (Instant/now) (Duration/ofHours 1)))]
      (is (true? (vote.logic/voting-open? {:status "closed" :closed-at future-time}))))))

(deftest user-adapter-edge-cases
  (testing "model->response with missing email"
    (let [id "00000000-0000-0000-0000-000000000001"
          user {:id id :name "No Email"}]
      (is (= {:id id :name "No Email" :admin_orgs [] :is_blocked false :is_super_admin false :allow_org_creation false} (adapter.user/model->response user)))))

  (testing "model->response with explicit false for exclude-email?"
    (let [id "00000000-0000-0000-0000-000000000001"
          user {:id id :name "Test" :email "t@t.com"}]
      (is (= {:id id :name "Test" :email "t@t.com" :admin_orgs [] :is_blocked false :is_super_admin false :allow_org_creation false} (adapter.user/model->response user false))))))
