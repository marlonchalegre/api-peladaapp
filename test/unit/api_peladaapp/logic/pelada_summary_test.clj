(ns api-peladaapp.logic.pelada-summary-test
  (:require
   [api-peladaapp.logic.pelada-summary :as summary]
   [clojure.test :refer [deftest is testing]]))

(def teams
  [{:id "a" :pelada_id "p1" :name "Time A"}
   {:id "b" :pelada_id "p1" :name "Time B"}
   {:id "c" :pelada_id "p1" :name "Time C"}])

(def matches
  [{:pelada_id "p1" :home_team_id "a" :away_team_id "b" :home_score 3 :away_score 1}
   {:pelada_id "p1" :home_team_id "a" :away_team_id "c" :home_score 2 :away_score 2}
   {:pelada_id "p1" :home_team_id "b" :away_team_id "c" :home_score 0 :away_score 1}])

(deftest test-team-standings
  (let [[first-team second-team third-team] (get (summary/team-standings matches teams) "p1")]
    (testing "points follow win=3, draw=1"
      (is (= 4 (:points first-team)))
      (is (= 4 (:points second-team)))
      (is (= 0 (:points third-team))))
    (testing "standings are ordered by final position"
      (is (= "Time A" (:team_name first-team)))
      (is (= 1 (:position first-team)))
      (is (= "Time C" (:team_name second-team)))
      (is (= 2 (:position second-team)))
      (is (= 3 (:position third-team))))
    (testing "goal difference is derived"
      (is (= 2 (:goal_diff first-team)))
      (is (= -3 (:goal_diff third-team))))))

(deftest test-champion-requires-a-played-match
  (is (= "Time A" (:team_name (summary/champion (get (summary/team-standings matches teams) "p1")))))
  (is (nil? (summary/champion (get (summary/team-standings [] teams) "p1")))))

(deftest test-teams-without-matches-rank-last
  (let [night-teams [{:id "a" :pelada_id "p1" :name "Winner"}
                     {:id "b" :pelada_id "p1" :name "Loser"}
                     {:id "c" :pelada_id "p1" :name "Bystander"}]
        night-matches [{:pelada_id "p1" :home_team_id "a" :away_team_id "b" :home_score 2 :away_score 0}]
        standings (get (summary/team-standings night-matches night-teams) "p1")]
    (testing "teams that never played rank after teams that played, even at zero points"
      (is (= ["Winner" "Loser" "Bystander"] (mapv :team_name standings)))
      (is (= 2 (:position (second standings))))
      (is (= 3 (:position (last standings)))))
    (testing "the champion is still the top team that actually played"
      (is (= "Winner" (:team_name (summary/champion standings)))))))

(deftest test-nil-scores-count-as-zero-draw
  (let [night-teams [{:id "a" :pelada_id "p1" :name "A"}
                     {:id "b" :pelada_id "p1" :name "B"}]
        night-matches [{:pelada_id "p1" :home_team_id "a" :away_team_id "b" :home_score nil :away_score nil}]
        standings (get (summary/team-standings night-matches night-teams) "p1")
        [team-a team-b] standings]
    (is (= 1 (:points team-a)))
    (is (= 1 (:points team-b)))
    (is (= 1 (:games team-a)))
    (is (= 1 (:draws team-a)))
    (is (= 0 (:goal_diff team-a)))
    (is (= "A" (:team_name (summary/champion standings))))))

(deftest test-matches-with-unknown-teams-or-peladas-are-ignored
  (let [night-teams [{:id "a" :pelada_id "p1" :name "A"}]
        night-matches [{:pelada_id "p1" :home_team_id "a" :away_team_id "ghost" :home_score 5 :away_score 0}
                       {:pelada_id "other-pelada" :home_team_id "a" :away_team_id "a" :home_score 1 :away_score 0}]
        result (summary/team-standings night-matches night-teams)
        [team-a] (get result "p1")]
    (testing "a match with an unknown opponent leaves standings untouched"
      (is (= 0 (:games team-a)))
      (is (= 0 (:points team-a))))
    (testing "matches of peladas without registered teams are dropped"
      (is (nil? (get result "other-pelada"))))))

(deftest test-awards
  (let [participants [{:player_id "1" :goals 2 :assists 0 :avg_stars 3.5 :vote_count 4}
                      {:player_id "2" :goals 1 :assists 3 :avg_stars 4.5 :vote_count 3}
                      {:player_id "3" :goals 0 :assists 0 :avg_stars 0.0 :vote_count 0}]
        awards (summary/awards participants)]
    (testing "MVP is the highest average stars among voted players"
      (is (= "2" (:player_id (:mvp awards)))))
    (testing "top scorer and assist leader come from the event counts"
      (is (= "1" (:player_id (:top-scorer awards))))
      (is (= "2" (:player_id (:garcom awards)))))
    (testing "no awards among players without events or votes"
      (is (nil? (some #(= "3" (:player_id %)) [(:mvp awards) (:top-scorer awards) (:garcom awards)]))))))

(deftest test-awards-tie-breaks-by-vote-count
  (let [participants [{:player_id "x" :goals 1 :assists 0 :avg_stars 4.0 :vote_count 2}
                      {:player_id "y" :goals 1 :assists 0 :avg_stars 4.0 :vote_count 5}]
        awards (summary/awards participants)]
    (testing "equal average stars are broken by who received more votes"
      (is (= "y" (:player_id (:mvp awards)))))
    (testing "equal goal counts are broken by vote count"
      (is (= "y" (:player_id (:top-scorer awards)))))
    (testing "nobody qualifies as garcom without assists"
      (is (nil? (:garcom awards))))))

(deftest test-awards-are-nil-when-nobody-qualifies
  (let [awards (summary/awards [{:player_id "z" :goals 0 :assists 0 :avg_stars 0.0 :vote_count 0}])]
    (is (nil? (:mvp awards)))
    (is (nil? (:top-scorer awards)))
    (is (nil? (:garcom awards)))
    (is (= {:mvp nil :top-scorer nil :garcom nil} awards))))
