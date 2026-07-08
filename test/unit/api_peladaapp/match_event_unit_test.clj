(ns api-peladaapp.match-event-unit-test
  (:require
   [api-peladaapp.db.match-event :as match-event-db]
   [api-peladaapp.logic.match-event]
   [clojure.test :refer [deftest is testing]]))

(deftest unqualify-row-drops-namespaces
  (let [player-id "00000000-0000-0000-0000-000000000013"
        row {:MatchEvents/player_id player-id
             :MatchEvents/goals 2
             :MatchEvents/assists 1
             :MatchEvents/own_goals 0
             :other "value"}
        result (#'match-event-db/unqualify-row row)]
    (is (= {:player_id player-id
            :goals 2
            :assists 1
            :own_goals 0
            :other "value"}
           result))))

(deftest test-canonical-type
  (testing "Throws when event-type is nil"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Missing event type"
         (api-peladaapp.logic.match-event/canonical-type nil))))

  (testing "Resolves alias to canonical name"
    (is (= "assist" (api-peladaapp.logic.match-event/canonical-type "assistencia")))
    (is (= "goal" (api-peladaapp.logic.match-event/canonical-type "gol")))
    (is (= "own_goal" (api-peladaapp.logic.match-event/canonical-type "gol_contra")))
    (is (= "own_goal" (api-peladaapp.logic.match-event/canonical-type "gol-contra"))))

  (testing "Accepts allowed event types directly"
    (is (= "goal" (api-peladaapp.logic.match-event/canonical-type "goal")))
    (is (= "drible" (api-peladaapp.logic.match-event/canonical-type :drible)))
    (is (= "falta" (api-peladaapp.logic.match-event/canonical-type "falta"))))

  (testing "Throws on invalid event types"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Invalid event type"
         (api-peladaapp.logic.match-event/canonical-type "invalid-event")))))

(deftest test-ensure-player-id
  (testing "Returns player-id when non-nil"
    (is (= "player-123" (api-peladaapp.logic.match-event/ensure-player-id "player-123"))))

  (testing "Throws when player-id is nil"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Missing player id"
         (api-peladaapp.logic.match-event/ensure-player-id nil)))))


