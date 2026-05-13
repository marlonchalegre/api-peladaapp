(ns api-peladaapp.match-event-unit-test
  (:require
   [api-peladaapp.db.match-event :as match-event-db]
   [clojure.test :refer [deftest is]]))

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
