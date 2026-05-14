(ns api-peladaapp.logic.notifications-test
  (:require
   [api-peladaapp.logic.notifications :as notifications]
   [clojure.test :refer [deftest is testing]]))

(deftest generate-start-message-test
  (testing "generates correct start message with new format"
    (let [teams [{:id (parse-uuid "00000000-0000-0000-0000-000000000001") :name "Time 1"}
                 {:id (parse-uuid "00000000-0000-0000-0000-000000000002") :name "Time 2"}]
          team-players [{:team_id (parse-uuid "00000000-0000-0000-0000-000000000001") :player_name "Jogador A" :position "goalkeeper"}
                        {:team_id (parse-uuid "00000000-0000-0000-0000-000000000001") :player_name "Jogador B" :position "defender"}
                        {:team_id (parse-uuid "00000000-0000-0000-0000-000000000002") :player_name "Jogador C" :position "striker"}]
          msg (notifications/generate-start-message teams team-players)]
      (is (re-find #"ESCALAÇÃO DA PELADA" msg))
      (is (re-find #"\*TIME 1\*" msg))
      (is (re-find #"• Jogador A" msg))
      (is (re-find #"G" msg))
      (is (re-find #"• Jogador B" msg))
      (is (re-find #"Z" msg))
      (is (re-find #"\*TIME 2\*" msg))
      (is (re-find #"• Jogador C" msg))
      (is (re-find #"A" msg))
      (is (re-find #"```" msg))))

  (testing "generates correct start message using is_goalkeeper flag"
    (let [teams [{:id (parse-uuid "00000000-0000-0000-0000-000000000001") :name "Time 1"}]
          team-players [{:team_id (parse-uuid "00000000-0000-0000-0000-000000000001") :player_name "Goleiro" :is_goalkeeper true :position "defender"}
                        {:team_id (parse-uuid "00000000-0000-0000-0000-000000000001") :player_name "Zagueiro" :is_goalkeeper false :position "defender"}]
          msg (notifications/generate-start-message teams team-players)]
      (is (re-find #"• Goleiro +G" msg))
      (is (re-find #"• Zagueiro +Z" msg)))))

(deftest generate-end-message-test
  (testing "generates correct end message with full summary"
    (let [data {:pelada {:scheduled-at "2023-01-01T10:00:00Z"}
                :teams [{:id (parse-uuid "00000000-0000-0000-0000-000000000001") :name "Time A"}
                        {:id (parse-uuid "00000000-0000-0000-0000-000000000002") :name "Time B"}]
                :matches [{:home-team-id (parse-uuid "00000000-0000-0000-0000-000000000001")
                           :away-team-id (parse-uuid "00000000-0000-0000-0000-000000000002")
                           :home-score 2 :away-score 1}]
                :events [{:player-id (parse-uuid "00000000-0000-0000-0000-000000000101") :event-type "goal"}
                         {:player-id (parse-uuid "00000000-0000-0000-0000-000000000101") :event-type "goal"}
                         {:player-id (parse-uuid "00000000-0000-0000-0000-000000000102") :event-type "assist"}]
                :lineups []
                :team-players [{:player_id (parse-uuid "00000000-0000-0000-0000-000000000101") :player_name "Artilheiro"}
                               {:player_id (parse-uuid "00000000-0000-0000-0000-000000000102") :player_name "Garçom"}]}
          msg (notifications/generate-end-message data)]
      (is (re-find #"Resumo da rodada 01/01" msg))
      (is (re-find #"Classificacao:" msg))
      (is (re-find #"Time A +3 pts" msg))
      (is (re-find #"Gols:" msg))
      (is (re-find #"Artilheiro +2" msg))
      (is (re-find #"Assistencias:" msg))
      (is (re-find #"Garçom +1" msg))
      (is (re-find #"```" msg)))))

(deftest generate-vote-ended-message-test
  (testing "generates correct vote ended message"
    (let [ranking [{:player-name "Jogador A" :score 9.5}
                   {:player-name "Jogador B" :score 8.0}
                   {:player-name "Jogador C" :score 7.5}
                   {:player-name "Jogador D" :score 6.0}]
          msg (notifications/generate-vote-ended-message ranking)]
      (is (re-find #"Ranking da Pelada!" msg))
      (is (re-find #"Jogador A" msg))
      (is (re-find #"9.5" msg))
      (is (re-find #"Jogador B" msg))
      (is (re-find #"8.0" msg))
      (is (re-find #"Jogador C" msg))
      (is (re-find #"7.5" msg))
      (is (re-find #"Jogador D" msg))
      (is (re-find #"6.0" msg)))))
