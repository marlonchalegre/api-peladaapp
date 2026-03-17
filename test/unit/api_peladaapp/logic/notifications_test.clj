(ns api-peladaapp.logic.notifications-test
  (:require
   [api-peladaapp.logic.notifications :as notifications]
   [clojure.test :refer [deftest is testing]]))

(deftest generate-start-message-test
  (testing "generates correct start message"
    (let [teams [{:id 1 :name "Time 1"} {:id 2 :name "Time 2"}]
          team-players [{:team_id 1 :player_name "Jogador A"}
                        {:team_id 1 :player_name "Jogador B"}
                        {:team_id 2 :player_name "Jogador C"}]
          msg (notifications/generate-start-message teams team-players)]
      (is (re-find #"⚽ \*Pelada Iniciada!* ⚽" msg))
      (is (re-find #"\*Time 1\*" msg))
      (is (re-find #"• Jogador A" msg))
      (is (re-find #"• Jogador B" msg))
      (is (re-find #"\*Time 2\*" msg))
      (is (re-find #"• Jogador C" msg)))))

(deftest generate-end-message-test
  (testing "generates correct end message"
    (let [stats [{:name "Jogador A" :goals 2 :assists 1 :own-goals 0}
                 {:name "Jogador B" :goals 0 :assists 0 :own-goals 1}]
          msg (notifications/generate-end-message stats)]
      (is (re-find #"🏁 \*Pelada Encerrada!* 🏁" msg))
      (is (re-find #"• \*Jogador A\*: 2 ⚽, 1 🅰️" msg))
      (is (re-find #"• \*Jogador B\*: 0 ⚽, 0 🅰️, 1 🤡" msg)))))

(deftest generate-vote-ended-message-test
  (testing "generates correct vote ended message"
    (let [ranking [{:player-name "Jogador A" :score 9.5}
                   {:player-name "Jogador B" :score 8.0}
                   {:player-name "Jogador C" :score 7.5}
                   {:player-name "Jogador D" :score 6.0}]
          msg (notifications/generate-vote-ended-message ranking)]
      (is (re-find #"🏆 \*Ranking da Pelada!* 🏆" msg))
      (is (re-find #"🥇 \*Jogador A\*: 9.5 ⭐" msg))
      (is (re-find #"🥈 \*Jogador B\*: 8.0 ⭐" msg))
      (is (re-find #"🥉 \*Jogador C\*: 7.5 ⭐" msg))
      (is (re-find #"• \*Jogador D\*: 6.0 ⭐" msg)))))
