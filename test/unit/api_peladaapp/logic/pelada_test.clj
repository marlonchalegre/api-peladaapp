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

  (testing "Throws when team count is too small for specific formats"
    (let [ex-final (try (pelada.logic/ensure-schedulable-team-count ["t1" "t2"] "championship_final") (catch Exception e e))
          ex-semis (try (pelada.logic/ensure-schedulable-team-count ["t1" "t2" "t3"] "championship_semis_final") (catch Exception e e))]
      (is (= "At least three teams are required for Group Stage + Final" (:message (ex-data ex-final))))
      (is (= "At least four teams are required for Group Stage + Semifinals & Final" (:message (ex-data ex-semis))))))

  (testing "Returns vector of team IDs when count >= required"
    (is (= ["t1" "t2"] (pelada.logic/ensure-schedulable-team-count '("t1" "t2"))))
    (is (= ["t1" "t2"] (pelada.logic/ensure-schedulable-team-count ["t1" "t2"] "winner_stays_on")))
    (is (= ["t1" "t2" "t3"] (pelada.logic/ensure-schedulable-team-count ["t1" "t2" "t3"] "championship_final")))
    (is (= ["t1" "t2" "t3" "t4"] (pelada.logic/ensure-schedulable-team-count ["t1" "t2" "t3" "t4"] "championship_semis_final")))
    (is (= ["t1" "t2" "t3"] (pelada.logic/ensure-schedulable-team-count ["t1" "t2" "t3"] "winner_stays_on")))))

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
      (is (= [{:home "t1" :away "t3" :stage "regular"} {:home "t2" :away "t3" :stage "regular"}]
             (pelada.logic/schedule-matches-for-start ["t1" "t2"] nil)))))

  (testing "Delegates to schedule-matches-with-limit when matches-per-team is provided"
    (with-redefs [schedule/schedule-matches-with-limit (fn [ids limit] (map #(hash-map :home %1 :away limit) ids))]
      (is (= [{:home "t1" :away 3 :stage "regular"} {:home "t2" :away 3 :stage "regular"}]
             (pelada.logic/schedule-matches-for-start ["t1" "t2"] 3)))))

  (testing "Assigns group stage for championship formats and single match for winner_stays_on"
    (with-redefs [schedule/schedule-matches-with-limit (fn [ids _] [{:home (first ids) :away (second ids)}])]
      (is (= [{:home "t1" :away "t2" :stage "group"}]
             (pelada.logic/schedule-matches-for-start ["t1" "t2" "t3"] 2 "championship_final")))
      (is (= [{:home "t1" :away "t2" :stage "regular"}]
             (pelada.logic/schedule-matches-for-start ["t1" "t2" "t3"] 2 "winner_stays_on"))))))

(deftest test-match-plan-to-rows
  (testing "Converts scheduled matches to DB ready rows"
    (let [p-id "pelada-1"
          matches [{:home "t1" :away "t2"} {:home "t3" :away "t4" :stage "final"}]
          expected [{:pelada-id p-id :home-team-id "t1" :away-team-id "t2" :sequence 1 :status "scheduled" :home-score 0 :away-score 0 :stage "regular"}
                    {:pelada-id p-id :home-team-id "t3" :away-team-id "t4" :sequence 2 :status "scheduled" :home-score 0 :away-score 0 :stage "final"}]]
      (is (= expected (pelada.logic/match-plan->rows p-id matches))))))

(deftest test-compute-winner-stays-on-next-match
  (testing "Challenger stays (default) on draw, winner stays on win, max consecutive wins rotates"
    (let [teams ["t1" "t2" "t3" "t4"]]
      ;; Match 1: t1 vs t2 -> 2-1 (t1 wins, t3 challenges)
      (is (= {:home "t1" :away "t3" :stage "regular"}
             (pelada.logic/compute-winner-stays-on-next-match
              teams
              [{:sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 2 :away-score 1}]
              {})))
      ;; Match 1: t1 vs t2 -> 1-1 draw -> challenger_stays means away (t2) stays, t4 is after t3
      (is (= {:home "t2" :away "t3" :stage "regular"}
             (pelada.logic/compute-winner-stays-on-next-match
              teams
              [{:sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 1 :away-score 1}]
              {:draw_rule "challenger_stays"})))
      ;; both_leave on draw with 4 teams -> t3 vs t4
      (is (= {:home "t3" :away "t4" :stage "regular"}
             (pelada.logic/compute-winner-stays-on-next-match
              teams
              [{:sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 1 :away-score 1}]
              {:draw_rule "both_leave"})))
      ;; max_consecutive_wins = 2 -> t1 wins twice -> rotates out!
      (is (= {:home "t4" :away "t2" :stage "regular"}
             (pelada.logic/compute-winner-stays-on-next-match
              teams
              [{:sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 2 :away-score 0}
               {:sequence 2 :home-team-id "t1" :away-team-id "t3" :home-score 1 :away-score 0}]
              {:max_consecutive_wins 2}))))

    (testing "2-team winner_stays_on rotates cleanly without nil away"
      (is (= {:home "t1" :away "t2" :stage "regular"}
             (pelada.logic/compute-winner-stays-on-next-match
              ["t1" "t2"]
              [{:sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 2 :away-score 1}]
              {})))
      (is (= {:home "t2" :away "t1" :stage "regular"}
             (pelada.logic/compute-winner-stays-on-next-match
              ["t1" "t2"]
              [{:sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 1 :away-score 2}]
              {}))))))

(defn- make-wso-lineup-fixture
  [{:keys [grades fixed-gks?] :or {grades {}}}]
  (let [confirmed (vec (concat
                        (when fixed-gks?
                          [{:player-id "gk1" :grade 7.0}
                           {:player-id "gk2" :grade 7.0}])
                        (map (fn [i]
                               (let [pid (str "p" i)]
                                 {:player-id pid :grade (get grades pid (+ 5.0 (* 0.1 i)))}))
                             (range 1 13))))
        team-players (concat
                      (map (fn [i] {:team_id "t1" :player_id (str "p" i)}) (range 1 6))
                      (map (fn [i] {:team_id "t2" :player_id (str "p" i)}) (range 6 11)))
        finished-matches [{:id "m1" :sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 2 :away-score 0 :status "finished"}]
        match-lineups (map (fn [{:keys [team_id player_id]}] {:match_id "m1" :team_id team_id :player_id player_id}) team-players)]
    {:confirmed confirmed
     :team-players team-players
     :finished-matches finished-matches
     :match-lineups match-lineups
     :next-spec {:home "t1" :away "t2" :stage "regular"}}))

(deftest test-compute-winner-stays-on-lineups
  (testing "12 players in 5v5: 2 bench players + 3 players from losing team form next challenger"
    (let [{:keys [confirmed team-players finished-matches match-lineups next-spec]} (make-wso-lineup-fixture {})
          pelada {:format "winner_stays_on"
                  :players-per-team 5
                  :format-config {:rotation_mode "player_queue"
                                  :complete_team_rule "least_played"}}
          res (pelada.logic/compute-winner-stays-on-lineups
               pelada next-spec finished-matches match-lineups team-players confirmed)]
      (is (= ["p1" "p2" "p3" "p4" "p5"] (:home-lineup res)))
      (is (= ["p11" "p12" "p6" "p7" "p8"] (:away-lineup res)))))

  (testing "Balanced and random complete_team_rule + fixed goalkeepers exclusion"
    (let [grades (into {"p8" 9.0} (map (fn [i] [(str "p" i) 5.0]) (remove #{8} (range 1 13))))
          {:keys [confirmed team-players finished-matches match-lineups next-spec]}
          (make-wso-lineup-fixture {:grades grades :fixed-gks? true})
          base-pelada {:format "winner_stays_on"
                       :players-per-team 5
                       :fixed-goalkeepers true
                       :home-fixed-goalkeeper-id "gk1"
                       :away-fixed-goalkeeper-id "gk2"}
          res-balanced (pelada.logic/compute-winner-stays-on-lineups
                        (assoc base-pelada :format-config {:rotation_mode "player_queue" :complete_team_rule "balanced"})
                        next-spec finished-matches match-lineups team-players confirmed)
          res-random (pelada.logic/compute-winner-stays-on-lineups
                      (assoc base-pelada :format-config {:rotation_mode "player_queue" :complete_team_rule "random"})
                      next-spec finished-matches match-lineups team-players confirmed)]
      ;; Fixed GKs (gk1, gk2) are excluded from outfield rotation; p8 (grade 9.0) is farthest from 5.0
      (is (= ["p11" "p12" "p6" "p7" "p9"] (:away-lineup res-balanced)))
      (is (= 5 (count (:away-lineup res-random))))
      (is (= #{"p11" "p12"} (set (take 2 (:away-lineup res-random)))))
      (is (every? #{"p6" "p7" "p8" "p9" "p10"} (drop 2 (:away-lineup res-random))))))

  (testing "Returns nil when team_queue has full teams and >= 3 teams"
    (let [confirmed (mapv (fn [i] {:player-id (str "p" i) :grade 7.0}) (range 1 16))
          team-players (concat
                        (map (fn [i] {:team_id "t1" :player_id (str "p" i)}) (range 1 6))
                        (map (fn [i] {:team_id "t2" :player_id (str "p" i)}) (range 6 11))
                        (map (fn [i] {:team_id "t3" :player_id (str "p" i)}) (range 11 16)))
          finished-matches [{:id "m1" :sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 2 :away-score 0 :status "finished"}]
          pelada {:format "winner_stays_on"
                  :players-per-team 5
                  :format-config {:rotation_mode "team_queue"}}
          next-spec {:home "t1" :away "t3" :stage "regular"}]
      (is (nil? (pelada.logic/compute-winner-stays-on-lineups
                 pelada next-spec finished-matches [] team-players confirmed)))))

  (testing "get-challenger-completion-context marks only fewest-consecutive-games candidates as eligible_by_fairness"
    (let [confirmed (mapv (fn [i] {:player-id (str "p" i) :grade 7.0}) (range 1 13))
          team-players (concat
                        (map (fn [i] {:team_id "t1" :player_id (str "p" i)}) (range 1 6))
                        (map (fn [i] {:team_id "t2" :player_id (str "p" i)}) (range 6 11)))
          ;; Match 1: t1 (p1..p5) vs t2 (p6..p10); Match 2: t1 vs t2 where p6 played 2 games in a row and p7..p10 only played Match 2
          finished-matches [{:id "m1" :sequence 1 :home-team-id "t1" :away-team-id "t2" :home-score 1 :away-score 0 :status "finished"}
                            {:id "m2" :sequence 2 :home-team-id "t1" :away-team-id "t2" :home-score 1 :away-score 0 :status "finished"}]
          match-lineups (concat
                         (map (fn [i] {:match_id "m1" :team_id "t1" :player_id (str "p" i)}) (range 1 6))
                         [{:match_id "m1" :team_id "t2" :player_id "p6"}
                          {:match_id "m1" :team_id "t2" :player_id "p11"}
                          {:match_id "m1" :team_id "t2" :player_id "p12"}]
                         (map (fn [i] {:match_id "m2" :team_id "t1" :player_id (str "p" i)}) (range 1 6))
                         (map (fn [i] {:match_id "m2" :team_id "t2" :player_id (str "p" i)}) (range 6 11)))
          pelada {:format "winner_stays_on"
                  :players-per-team 5
                  :format-config {:rotation_mode "player_queue" :complete_team_rule "least_played"}}
          target-match {:id "m3" :sequence 3 :home-team-id "t1" :away-team-id "t2"}
          ctx (pelada.logic/get-challenger-completion-context
               pelada target-match finished-matches match-lineups team-players confirmed)
          by-id (into {} (map (juxt :player_id identity) (:candidates ctx)))]
      (is (= 3 (:needed ctx)))
      (is (= ["p11" "p12"] (:bench_player_ids ctx)))
      ;; p6 played 2 consecutive games while p7..p10 played 1 consecutive game; 3 spots needed -> p7..p10 eligible, p6 not eligible
      (is (false? (:eligible_by_fairness (get by-id "p6"))))
      (is (true? (:eligible_by_fairness (get by-id "p7")))))))
