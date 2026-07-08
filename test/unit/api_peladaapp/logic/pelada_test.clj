(ns api-peladaapp.logic.pelada-test
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.pelada :as pelada.logic]
   [api-peladaapp.logic.schedule :as schedule]
   [api-peladaapp.logic.vote :as logic.vote]
   [clojure.test :refer [deftest is testing]]))

(deftest test-ensure-open
  (testing "Returns pelada when status is 'open'"
    (let [pelada {:status "open" :id "some-id"}]
      (is (= pelada (pelada.logic/ensure-open pelada)))))

  (testing "Throws bad request when status is 'attendance'"
    (let [ex (try (pelada.logic/ensure-open {:status "attendance"})
                  (catch Exception e e))]
      (is (= "Attendance list is still open. Close it before starting the pelada."
             (:message (ex-data ex))))))

  (testing "Throws bad request when status is anything else"
    (let [ex1 (try (pelada.logic/ensure-open {:status "running"}) (catch Exception e e))
          ex2 (try (pelada.logic/ensure-open {:status "closed"}) (catch Exception e e))]
      (is (= "Pelada already started or closed" (:message (ex-data ex1))))
      (is (= "Pelada already started or closed" (:message (ex-data ex2)))))))

(deftest test-ensure-running
  (testing "Returns pelada when status is 'running'"
    (let [pelada {:status "running"}]
      (is (= pelada (pelada.logic/ensure-running pelada)))
      (is (= pelada (pelada.logic/ensure-running pelada {:allow-closed? true})))))

  (testing "Returns pelada when status is closed or voting and allow-closed? option is true"
    (let [closed-pelada {:status "closed"}
          voting-pelada {:status "voting"}]
      (is (= closed-pelada (pelada.logic/ensure-running closed-pelada {:allow-closed? true})))
      (is (= voting-pelada (pelada.logic/ensure-running voting-pelada {:allow-closed? true})))))

  (testing "Throws when not running and allow-closed? option is false or omitted"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Pelada is not running"
         (pelada.logic/ensure-running {:status "closed"})))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Pelada is not running"
         (pelada.logic/ensure-running {:status "voting"})))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Pelada is not running"
         (pelada.logic/ensure-running {:status "open"})))))

(deftest test-ensure-schedulable-team-count
  (testing "Throws when team count is less than 2"
    (let [ex1 (try (pelada.logic/ensure-schedulable-team-count ["team-1"]) (catch Exception e e))
          ex2 (try (pelada.logic/ensure-schedulable-team-count []) (catch Exception e e))]
      (is (= "At least two teams are required" (:message (ex-data ex1))))
      (is (= "At least two teams are required" (:message (ex-data ex2))))))

  (testing "Returns vector of team IDs when count >= 2"
    (is (= ["t1" "t2"] (pelada.logic/ensure-schedulable-team-count '("t1" "t2"))))))

(deftest test-get-voting-info
  (let [p-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        v-id (parse-uuid "00000000-0000-0000-0000-000000000002")]
    (testing "Returns can-vote true and has-voted false"
      (with-redefs [db.pelada/get-pelada (fn [_ _] {:id p-id :status "voting"})
                    logic.vote/validate-voting-eligibility (fn [_] nil)
                    db.vote/has-voter-voted? (fn [_ _ _] false)]
        (is (= {:can-vote true
                :has-voted false
                :eligible-players []
                :message ""}
               (pelada.logic/get-voting-info p-id v-id {})))))

    (testing "Returns can-vote false when validation throws"
      (with-redefs [db.pelada/get-pelada (fn [_ _] {:id p-id :status "closed"})
                    logic.vote/validate-voting-eligibility (fn [_] (throw (Exception. "closed")))
                    db.vote/has-voter-voted? (fn [_ _ _] true)]
        (is (= {:can-vote false
                :has-voted true
                :eligible-players []
                :message "Voting is not open or has closed."}
               (pelada.logic/get-voting-info p-id v-id {})))))))

(deftest test-ensure-startable
  (testing "Succeeds when pelada is open and there are >= 2 teams"
    (is (= ["t1" "t2"] (pelada.logic/ensure-startable {:status "open"} ["t1" "t2"])))))

(deftest test-schedule-matches-for-start
  (testing "Delegates to schedule-matches when matches-per-team is nil"
    (with-redefs [schedule/schedule-matches (fn [ids] (map #(hash-map :home %1 :away "t3") ids))]
      (is (= [{:home "t1" :away "t3"} {:home "t2" :away "t3"}]
             (pelada.logic/schedule-matches-for-start ["t1" "t2"] nil)))))

  (testing "Delegates to schedule-matches-with-limit when matches-per-team is provided"
    (with-redefs [schedule/schedule-matches-with-limit (fn [ids limit] (map #(hash-map :home %1 :away limit) ids))]
      (is (= [{:home "t1" :away 3} {:home "t2" :away 3}]
             (pelada.logic/schedule-matches-for-start ["t1" "t2"] 3))))))

(deftest test-match-plan-to-rows
  (testing "Converts scheduled matches to DB ready rows"
    (let [p-id "pelada-1"
          matches [{:home "t1" :away "t2"} {:home "t3" :away "t4"}]
          expected [{:pelada-id p-id :home-team-id "t1" :away-team-id "t2" :sequence 1 :status "scheduled" :home-score 0 :away-score 0}
                    {:pelada-id p-id :home-team-id "t3" :away-team-id "t4" :sequence 2 :status "scheduled" :home-score 0 :away-score 0}]]
      (is (= expected (pelada.logic/match-plan->rows p-id matches))))))
