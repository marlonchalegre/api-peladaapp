(ns api-100folego.match-event-unit-test
  (:require [clojure.test :refer :all]
            [api-100folego.db.match-event :as match-event-db]))

(deftest unqualify-row-drops-namespaces
  (let [row {:MatchEvents/player_id 13
             :MatchEvents/goals 2
             :MatchEvents/assists 1
             :MatchEvents/own_goals 0
             :other "value"}
        result (#'match-event-db/unqualify-row row)]
    (is (= {:player_id 13
            :goals 2
            :assists 1
            :own_goals 0
            :other "value"}
           result))))
