(ns api-peladaapp.controllers.substitution-test
  (:require
   [api-peladaapp.controllers.substitution :as sub.controller]
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.substitution :as db.substitution]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.logic.substitution :as substitution.logic]
   [clojure.test :refer [deftest is testing]]))

(deftest test-create-substitution
  (let [match-uuid (random-uuid)
        pelada-uuid (random-uuid)
        team-uuid (random-uuid)
        player-uuid (random-uuid)
        sub-payload {:match-id match-uuid :player-in player-uuid :player-out (random-uuid)}
        dummy-match {:id match-uuid :pelada-id pelada-uuid}
        db nil]
    (testing "when match does not exist, throws not found exception"
      (with-redefs [db.match/get-match (fn [_ _] nil)]
        (try
          (sub.controller/create-substitution sub-payload db)
          (is false "Expected exception")
          (catch Exception ex
            (let [data (ex-data ex)]
              (is (= :not-found (:type data)))
              (is (= "Match not found" (:message data))))))))

    (testing "when match exists, runs validation and inserts substitution"
      (with-redefs [db.match/get-match (fn [id _] (is (= match-uuid id)) dummy-match)
                    db.team/list-pelada-teams (fn [pelada-id _]
                                                (is (= pelada-uuid pelada-id))
                                                [{:id team-uuid}])
                    db.team/list-team-players (fn [team-id _]
                                                (is (= team-uuid team-id))
                                                [{:player_id player-uuid}])
                    substitution.logic/validate-substitution (fn [sub allowed]
                                                               (is (= sub-payload sub))
                                                               (is (= #{player-uuid} allowed))
                                                               nil)
                    db.substitution/insert-substitution (fn [sub _]
                                                          (is (= sub-payload sub))
                                                          1)]
        (is (= 1 (sub.controller/create-substitution sub-payload db)))))))

(deftest test-list-substitutions
  (let [match-uuid (random-uuid)
        dummy-subs [{:id (random-uuid)}]
        db nil]
    (with-redefs [db.substitution/list-substitutions (fn [match-id _]
                                                       (is (= match-uuid match-id))
                                                       dummy-subs)]
      (is (= dummy-subs (sub.controller/list-substitutions match-uuid db))))))
