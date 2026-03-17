(ns api-peladaapp.logic.notifications
  (:require
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.logic.waha :as waha]
   [clojure.string :as str]))

(defn generate-start-message [teams team-players]
  (let [title "⚽ *Pelada Iniciada!* ⚽\n\nOs times foram sorteados. Boa sorte a todos!\n\n"
        teams-grouped (group-by :team_id team-players)
        teams-str (->> teams
                       (map (fn [team]
                              (let [players (get teams-grouped (:id team) [])
                                    players-str (->> players
                                                     (map #(str "• " (:player_name %)))
                                                     (str/join "\n"))]
                                (str "*" (:name team) "*\n" players-str))))
                       (str/join "\n\n"))]
    (str title teams-str)))

(defn generate-end-message [stats]
  (let [title "🏁 *Pelada Encerrada!* 🏁\n\nConfira as estatísticas da partida:\n\n"
        stats-str (->> stats
                       (map (fn [s]
                              (str "• *" (:name s) "*: " (:goals s) " ⚽, " (:assists s) " 🅰️"
                                   (if (pos? (:own-goals s)) (str ", " (:own-goals s) " 🤡") ""))))
                       (str/join "\n"))]
    (str title stats-str "\n\nNão esqueçam de votar nos melhores da pelada no app!")))

(defn generate-vote-ended-message [ranking]
  (let [title "🏆 *Ranking da Pelada!* 🏆\n\nA votação encerrou. Confira os destaques:\n\n"
        ranking-str (->> ranking
                         (map-indexed (fn [idx r]
                                        (let [medal (case idx 0 "🥇" 1 "🥈" 2 "🥉" "•")]
                                          (str medal " *" (:player-name r) "*: " (format "%.1f" (:score r)) " ⭐"))))
                         (str/join "\n"))]
    (str title ranking-str)))

(defn generate-attendance-reminder [pending-players]
  (let [title "⏰ *Lembrete de Presença!* ⏰\n\nAinda temos jogadores com presença pendente para a próxima pelada:\n\n"
        players-str (->> pending-players
                         (map #(str "• " (:player-name %)))
                         (str/join "\n"))]
    (str title players-str "\n\nPor favor, confirmem no app o quanto antes!")))

(defn generate-vote-reminder [pending-voters]
  (let [title "🗳️ *Lembrete de Votação!* 🗳️\n\nAinda faltam alguns jogadores votarem nos melhores da pelada:\n\n"
        players-str (->> pending-voters
                         (map #(str "• " (:player-name %)))
                         (str/join "\n"))]
    (str title players-str "\n\nAcesse o app e deixe seu voto!")))

(defn send-notification!
  "Sends a notification if enabled for the organization."
  [org-id type data db]
  (let [org (db.organization/get-organization org-id db)]
    (when (and org (:waha-enabled org))
      (let [enabled-key (case type
                          :start :waha-start-msg-enabled
                          :end :waha-end-msg-enabled
                          :vote-ended :waha-vote-ended-msg-enabled
                          :attendance-reminder :waha-attendance-reminder-enabled
                          :vote-reminder :waha-vote-reminder-enabled)
            should-send? (get org enabled-key)]
        (when should-send?
          (let [message (case type
                          :start (generate-start-message (:teams data) (:team-players data))
                          :end (generate-end-message (:stats data))
                          :vote-ended (generate-vote-ended-message (:ranking data))
                          :attendance-reminder (generate-attendance-reminder (:pending-players data))
                          :vote-reminder (generate-vote-reminder (:pending-voters data)))]
            (waha/send-message org message)))))))
