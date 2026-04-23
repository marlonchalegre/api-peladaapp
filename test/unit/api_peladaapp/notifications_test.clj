(ns api-peladaapp.notifications-test
  (:require
   [api-peladaapp.logic.notifications :as notifications]
   [clojure.test :refer [deftest is testing]]))

(deftest format-mention-test
  (testing "With phone number"
    (let [player {:player-name "Test" :phone "5511999999999"}]
      (is (= "@5511999999999" (#'notifications/format-mention player)))))

  (testing "Without phone number"
    (let [player {:player-name "Test Name" :phone nil}]
      (is (= "Test Name" (#'notifications/format-mention player))))))

(deftest generate-attendance-reminder-test
  (let [players [{:player-name "User 1" :phone "5511911111111"}
                 {:player-name "User 2" :phone nil}]
        message (notifications/generate-attendance-reminder players)]
    (is (re-find #"• @5511911111111" message))
    (is (re-find #"• User 2" message))))

(deftest generate-vote-reminder-test
  (let [players [{:player-name "User 1" :phone "5511911111111"}
                 {:player-name "User 2" :phone nil}]
        message (notifications/generate-vote-reminder 123 players)]
    (is (re-find #"• @5511911111111" message))
    (is (re-find #"• User 2" message))
    (is (re-find #"/peladas/123/voting" message))))
