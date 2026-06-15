(ns api-peladaapp.notifications-test
  (:require
   [api-peladaapp.logic.notifications :as notifications]
   [clojure.test :refer [deftest is testing]]))

(deftest format-mention-test
  (testing "With phone number and 1 word name"
    (let [player {:player-name "Test" :phone "5511999999999"}]
      (is (= "@5511999999999 (Test)" (#'notifications/format-mention player)))))

  (testing "With phone number and 3 words name"
    (let [player {:player-name "First Second Third" :phone "5511999999999"}]
      (is (= "@5511999999999 (First Second)" (#'notifications/format-mention player)))))

  (testing "Without phone number"
    (let [player {:player-name "Test Name" :phone nil}]
      (is (= "Test Name" (#'notifications/format-mention player))))))

(deftest generate-attendance-reminder-test
  (let [players [{:player-name "User Long Name" :phone "5511911111111"}
                 {:player-name "User 2" :phone nil}]
        pelada-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        message (notifications/generate-attendance-reminder pelada-id players)]
    (is (re-find #"• @5511911111111 \(User Long\)" message))
    (is (re-find #"• User 2" message))
    (is (re-find #"/peladas/00000000-0000-0000-0000-000000000001" message))))

(deftest generate-new-pelada-message-test
  (testing "Generates new pelada notification"
    (let [pelada-id (parse-uuid "00000000-0000-0000-0000-000000000001")
          message (notifications/generate-new-pelada-message pelada-id "2023-01-01T10:00:00Z" [])]
      (is (re-find #"⚽ \*Nova Pelada Confirmada!\* ⚽" message))
      (is (re-find #"01/01" message))
      (is (re-find #"/peladas/00000000-0000-0000-0000-000000000001" message))
      (is (re-find #"Nenhum jogador confirmado ainda." message)))))

(deftest generate-vote-reminder-test
  (let [players [{:player-name "User One Two Three" :phone "5511911111111"}
                 {:player-name "User 2" :phone nil}]
        message (notifications/generate-vote-reminder 123 players)]
    (is (re-find #"• @5511911111111 \(User One\)" message))
    (is (re-find #"• User 2" message))
    (is (re-find #"/peladas/123/voting" message))))
