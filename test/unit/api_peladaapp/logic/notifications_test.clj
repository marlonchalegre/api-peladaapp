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
  (testing "generates correct end message with full round summary (standings, goals, assists)"
    (let [p-id (parse-uuid "00000000-0000-0000-0000-000000000099")
          t1 (parse-uuid "00000000-0000-0000-0000-000000000001")
          t2 (parse-uuid "00000000-0000-0000-0000-000000000002")
          p1 (parse-uuid "00000000-0000-0000-0000-000000000101")
          p2 (parse-uuid "00000000-0000-0000-0000-000000000102")
          data {:pelada {:id p-id :scheduled-at "2026-08-12T10:00:00Z"}
                :teams [{:id t1 :name "Time A"}
                        {:id t2 :name "Time B"}]
                :matches [{:home-team-id t1 :away-team-id t2 :home-score 2 :away-score 1}
                          {:home-team-id t2 :away-team-id t1 :home-score 3 :away-score 0}]
                :events [{:player-id p1 :event-type "goal"}
                         {:player-id p1 :event-type "goal"}
                         {:player-id p2 :event-type "assist"}]
                :team-players [{:player_id p1 :player_name "Artilheiro"}
                               {:player_id p2 :player_name "Garçom"}]}
          msg (notifications/generate-end-message data)]
      (is (re-find #"Resumo da rodada 12/08" msg))
      (is (re-find #"Classificacao:" msg))
      ;; Time B: 3 pts (1V 0E 1D) GP:4 SG:+2
      (is (re-find #"Time B +3 pts \(1V 0E 1D\) GP:4 SG:\+2" msg))
      ;; Time A: 3 pts (1V 0E 1D) GP:2 SG:-2
      (is (re-find #"Time A +3 pts \(1V 0E 1D\) GP:2 SG:-2" msg))
      (is (re-find #"Gols:" msg))
      (is (re-find #"Artilheiro 2" msg))
      (is (re-find #"Assistencias:" msg))
      (is (re-find #"Garçom 1" msg))
      (is (re-find #"Votação Aberta" msg))
      (is (re-find #"```" msg))))

  (testing "edge case: prevents double-slash in voting URL when pelada object or pelada-id is provided"
    (let [p-id (parse-uuid "11111111-2222-3333-4444-555555555555")
          msg (notifications/generate-end-message {:pelada {:id p-id}
                                                   :teams []
                                                   :matches []
                                                   :events []
                                                   :team-players []})]
      (is (re-find #"/peladas/11111111-2222-3333-4444-555555555555/voting" msg))
      (is (not (re-find #"/peladas//voting" msg)))))

  (testing "edge case: handles empty events, matches, and teams gracefully without throwing"
    (let [msg (notifications/generate-end-message {:pelada {} :teams [] :matches [] :events [] :team-players []})]
      (is (re-find #"Resumo da rodada" msg))
      (is (not (re-find #"Classificacao:" msg)))
      (is (not (re-find #"Gols:" msg)))
      (is (not (re-find #"Assistencias:" msg)))))

  (testing "edge case: standings tie-breaking (points > wins > SG > GP > name)"
    (let [t1 (parse-uuid "00000000-0000-0000-0000-000000000001")
          t2 (parse-uuid "00000000-0000-0000-0000-000000000002")
          t3 (parse-uuid "00000000-0000-0000-0000-000000000003")
          data {:teams [{:id t1 :name "Time Alpha"}
                        {:id t2 :name "Time Beta"}
                        {:id t3 :name "Time Gamma"}]
                ;; t1 and t2 both have 3 pts (1 win each).
                ;; t1: 1 win, GP 3, GC 1 -> SG +2
                ;; t2: 1 win, GP 4, GC 0 -> SG +4  => t2 should be 1st, t1 should be 2nd
                :matches [{:home-team-id t1 :away-team-id t3 :home-score 3 :away-score 1}
                          {:home-team-id t2 :away-team-id t3 :home-score 4 :away-score 0}]
                :events []
                :team-players []}
          msg (notifications/generate-end-message data)
          idx-beta (.indexOf msg "Time Beta")
          idx-alpha (.indexOf msg "Time Alpha")]
      ;; Time Beta should appear before Time Alpha in standings
      (is (> idx-alpha -1))
      (is (> idx-beta -1))
      (is (< idx-beta idx-alpha)))))

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

(deftest generate-matches-results-message-test
  (testing "generates correct match results message with aligned names and scores"
    (let [data {:teams [{:id (parse-uuid "00000000-0000-0000-0000-000000000001") :name "Short"}
                        {:id (parse-uuid "00000000-0000-0000-0000-000000000002") :name "VeryLongTeamName"}]
                :matches [{:home-team-id (parse-uuid "00000000-0000-0000-0000-000000000001")
                           :away-team-id (parse-uuid "00000000-0000-0000-0000-000000000002")
                           :home-score 3 :away-score 2}
                          {:home-team-id (parse-uuid "00000000-0000-0000-0000-000000000002")
                           :away-team-id (parse-uuid "00000000-0000-0000-0000-000000000001")
                           :home-score 0 :away-score 0}]}
          msg (notifications/generate-matches-results-message data)]
      (is (re-find #"⚽ \*RESULTADOS DAS PARTIDAS\*" msg))
      (is (re-find #"           Short  3 x 2  VeryLongTeamName" msg))
      (is (re-find #"VeryLongTeamName  0 x 0  Short           " msg))
      (is (re-find #"```" msg))))

  (testing "generates match results message with goalkeeper stats when lineups and team-players are provided"
    (let [p1 (parse-uuid "00000000-0000-0000-0000-000000000101")
          p2 (parse-uuid "00000000-0000-0000-0000-000000000102")
          t1 (parse-uuid "00000000-0000-0000-0000-000000000001")
          t2 (parse-uuid "00000000-0000-0000-0000-000000000002")
          m1 (parse-uuid "00000000-0000-0000-0000-000000000201")
          data {:teams [{:id t1 :name "Time A"}
                        {:id t2 :name "Time B"}]
                :matches [{:id m1 :match_id m1
                           :home-team-id t1 :away-team-id t2
                           :home-score 2 :away-score 3}]
                :team-players [{:player_id p1 :player_name "Goleiro Um"}
                               {:player_id p2 :player_name "Goleiro Dois"}]
                :lineups [{:match_id m1 :team_id t1 :player_id p1 :is_goalkeeper 1}
                          {:match_id m1 :team_id t2 :player_id p2 :is_goalkeeper 1}]}
          msg (notifications/generate-matches-results-message data)]
      (is (re-find #"⚽ \*RESULTADOS DAS PARTIDAS\*" msg))
      (is (re-find #"Time A  2 x 3  Time B" msg))
      (is (re-find #"Gols sofridos:" msg))
      (is (re-find #"Goleiro Dois" msg))
      (is (re-find #"Goleiro Um" msg))))

  (testing "generates goalkeeper goals conceded using real DB data format (matches with :id only, lineups with boolean :is_goalkeeper)"
    (let [p1 (parse-uuid "00000000-0000-0000-0000-000000000101")
          p2 (parse-uuid "00000000-0000-0000-0000-000000000102")
          p3 (parse-uuid "00000000-0000-0000-0000-000000000103")
          t1 (parse-uuid "00000000-0000-0000-0000-000000000001")
          t2 (parse-uuid "00000000-0000-0000-0000-000000000002")
          m1 (parse-uuid "00000000-0000-0000-0000-000000000201")
          data {:teams [{:id t1 :name "Time A"}
                        {:id t2 :name "Time B"}]
                :matches [{:id m1
                           :home-team-id t1 :away-team-id t2
                           :home-score 2 :away-score 3}]
                :team-players [{:player_id p1 :player_name "Goleiro Um"}
                               {:player_id p2 :player_name "Goleiro Dois"}
                               {:player_id p3 :player_name "Linha Tres"}]
                :lineups [{:match_id m1 :team_id t1 :player_id p1 :is_goalkeeper true}
                          {:match_id m1 :team_id t2 :player_id p2 :is_goalkeeper true}
                          {:match_id m1 :team_id t1 :player_id p3 :is_goalkeeper false}]}
          msg (notifications/generate-matches-results-message data)]
      (is (re-find #"Gols sofridos:" msg))
      (is (re-find #"Goleiro Dois" msg))
      (is (re-find #"Goleiro Um" msg))
      (is (not (re-find #"Linha Tres" msg)))))

  (testing "edge case: falls back to team-players goalkeepers when lineups list is empty"
    (let [p1 (parse-uuid "00000000-0000-0000-0000-000000000101")
          t1 (parse-uuid "00000000-0000-0000-0000-000000000001")
          t2 (parse-uuid "00000000-0000-0000-0000-000000000002")
          m1 (parse-uuid "00000000-0000-0000-0000-000000000201")
          data {:teams [{:id t1 :name "Time A"}
                        {:id t2 :name "Time B"}]
                :matches [{:id m1 :home-team-id t1 :away-team-id t2 :home-score 1 :away-score 4}]
                :lineups []
                :team-players [{:player_id p1 :team_id t1 :player_name "Goleiro Time A" :is_goalkeeper true}]}
          msg (notifications/generate-matches-results-message data)]
      (is (re-find #"Gols sofridos:" msg))
      (is (re-find #"Goleiro Time A 4" msg)))))

(testing "detects goalkeeper with Portuguese position 'Goleiro' in lineups even if is_goalkeeper is false"
  (let [p1 (parse-uuid "00000000-0000-0000-0000-000000000101")
        p2 (parse-uuid "00000000-0000-0000-0000-000000000102")
        t1 (parse-uuid "00000000-0000-0000-0000-000000000001")
        t2 (parse-uuid "00000000-0000-0000-0000-000000000002")
        m1 (parse-uuid "00000000-0000-0000-0000-000000000201")
        data {:teams [{:id t1 :name "Time A"}
                      {:id t2 :name "Time B"}]
              :matches [{:id m1 :home-team-id t1 :away-team-id t2 :home-score 1 :away-score 3}]
              :lineups [{:match_id m1 :team_id t1 :player_id p1 :is_goalkeeper false :position "Goleiro"}
                        {:match_id m1 :team_id t2 :player_id p2 :is_goalkeeper false :position "Goleiro"}]
              :team-players [{:player_id p1 :player_name "Goleiro PT 1"}
                             {:player_id p2 :player_name "Goleiro PT 2"}]}
        msg (notifications/generate-matches-results-message data)]
    (is (re-find #"Gols sofridos:" msg))
    (is (re-find #"Goleiro PT 1 3" msg))
    (is (re-find #"Goleiro PT 2 1" msg)))

  (testing "detects fixed goalkeepers set on pelada object"
    (let [p1 (parse-uuid "00000000-0000-0000-0000-000000000101")
          p2 (parse-uuid "00000000-0000-0000-0000-000000000102")
          t1 (parse-uuid "00000000-0000-0000-0000-000000000001")
          t2 (parse-uuid "00000000-0000-0000-0000-000000000002")
          m1 (parse-uuid "00000000-0000-0000-0000-000000000201")
          data {:pelada {:home-fixed-goalkeeper-id p1 :away-fixed-goalkeeper-id p2}
                :teams [{:id t1 :name "Time A"}
                        {:id t2 :name "Time B"}]
                :matches [{:id m1 :home-team-id t1 :away-team-id t2 :home-score 5 :away-score 2}]
                :lineups []
                :team-players [{:player_id p1 :player_name "Fixed Home GK"}
                               {:player_id p2 :player_name "Fixed Away GK"}]}
          msg (notifications/generate-matches-results-message data)]
      (is (re-find #"Gols sofridos:" msg))
      (is (re-find #"Fixed Home GK 2" msg))
      (is (re-find #"Fixed Away GK 5" msg)))))
