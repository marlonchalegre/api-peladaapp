(ns api-peladaapp.logic.notifications
  (:require
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.waha :as waha]
   [clojure.string :as str])
  (:import
   [java.time ZoneId]
   [java.time.format DateTimeFormatter]))

(def position-order
  {"goalkeeper" 0
   "defender" 1
   "midfielder" 2
   "striker" 3})

(def ^:private all-mention-types
  #{:new-pelada :attendance-reminder :priority-ending :vote-reminder})

(defn- sort-players [players]
  (sort-by (fn [p]
             [(if (:is_goalkeeper p) 0 1)
              (get position-order (some-> (:position p) str/lower-case) 4)
              (:player_name p)])
           players))

(defn- pad-end [s length]
  (if (>= (count s) length)
    s
    (str s (str/join (repeat (- length (count s)) " ")))))

(defn- pad-start [s length]
  (if (>= (count s) length)
    s
    (str (str/join (repeat (- length (count s)) " ")) s)))

(defn- get-base-url []
  (or (System/getenv "FRONTEND_URL") "http://localhost:5173"))

(defn- generate-voting-link [pelada-id]
  (str (get-base-url) "/peladas/" pelada-id "/voting"))

(defn- generate-results-link [pelada-id]
  (str (get-base-url) "/peladas/" pelada-id "/results"))

(defn- calculate-name-width [players]
  (let [max-len (if (seq players)
                  (reduce max 0 (map #(count (:player_name %)) players))
                  0)]
    (+ (max 15 (min 30 max-len)) 2)))

(defn generate-start-message [teams team-players]
  (let [title "*ESCALAÇÃO DA PELADA*\n\n"
        teams-grouped (group-by :team_id team-players)
        name-width (calculate-name-width team-players)

        pos-map {"goalkeeper" "G"
                 "defender" "Z"
                 "midfielder" "M"
                 "striker" "A"}

        teams-str (->> teams
                       (map (fn [team]
                              (let [players (get teams-grouped (:id team) [])
                                    sorted-players (sort-players players)
                                    players-str (->> sorted-players
                                                     (map (fn [p]
                                                            (let [pos (if (:is_goalkeeper p)
                                                                        "G"
                                                                        (get pos-map (some-> (:position p) str/lower-case) "?"))]
                                                              (str "• " (pad-end (:player_name p) name-width) pos))))
                                                     (str/join "\n"))]
                                (str "*" (str/upper-case (:name team)) "*\n```\n" players-str "\n```"))))
                       (str/join "\n\n"))]
    (str title teams-str)))

(defn- format-date [date-str]
  (try
    (let [instant (helpers.time/->instant date-str)
          formatter (-> (DateTimeFormatter/ofPattern "dd/MM")
                        (.withZone (ZoneId/of "America/Sao_Paulo")))]
      (.format formatter instant))
    (catch Exception _ "")))

(defn- is-goalkeeper-lineup? [l]
  (let [val (:is_goalkeeper l (get l :is-goalkeeper))]
    (or (= val true)
        (and (number? val) (not= val 0)))))

(defn- calculate-goalkeeper-goals-conceded [matches lineups]
  (reduce (fn [acc m]
            (let [m-id (or (:id m) (:match_id m) (:match-id m))
                  home-team-id (or (:home-team-id m) (:home_team_id m))
                  home-score (or (:home-score m) (:home_score m) 0)
                  away-score (or (:away-score m) (:away_score m) 0)
                  lu (filter #(= (str m-id) (str (or (:match_id %) (:match-id %) (:id %)))) (or lineups []))]
              (reduce (fn [inner-acc l]
                        (if (is-goalkeeper-lineup? l)
                          (let [team-id (or (:team_id l) (:team-id l))
                                player-id (or (:player_id l) (:player-id l))
                                conceded (if (= (str team-id) (str home-team-id))
                                           away-score
                                           home-score)]
                            (update inner-acc player-id (fnil + 0) conceded))
                          inner-acc))
                      acc lu)))
          {} (or matches [])))

(defn generate-end-message [{:keys [pelada matches teams events lineups team-players]}]
  (let [date-str (format-date (:scheduled-at pelada))
        title (str "Resumo da rodada " date-str "\n\nClassificacao:\n")

        ;; Name width calculation
        max-name-len (->> team-players
                          (map #(count (:player_name %)))
                          (reduce max 0))
        name-width (+ (max 15 (min 30 max-name-len)) 2)

        ;; Standings
        standings (-> (reduce (fn [acc t]
                                (assoc acc (:id t) {:wins 0 :draws 0 :losses 0 :goals-for 0 :goals-against 0 :name (:name t)}))
                              {} teams)
                      (as-> table
                            (reduce (fn [acc m]
                                      (let [hs (or (:home-score m) 0)
                                            as (or (:away-score m) 0)
                                            hid (:home-team-id m)
                                            aid (:away-team-id m)]
                                        (if (and (contains? acc hid) (contains? acc aid))
                                          (let [acc (-> acc
                                                        (update-in [hid :goals-for] + hs)
                                                        (update-in [hid :goals-against] + as)
                                                        (update-in [aid :goals-for] + as)
                                                        (update-in [aid :goals-against] + hs))]
                                            (cond
                                              (= hs as) (-> acc (update-in [hid :draws] inc) (update-in [aid :draws] inc))
                                              (> hs as) (-> acc (update-in [hid :wins] inc) (update-in [aid :losses] inc))
                                              :else (-> acc (update-in [hid :losses] inc) (update-in [aid :wins] inc))))
                                          acc)))
                                    table matches)))

        sorted-standings (sort-by (fn [[_ s]]
                                    [(- (+ (* (:wins s) 3) (:draws s)))
                                     (- (- (:goals-for s) (:goals-against s)))
                                     (- (:goals-for s))
                                     (:name s)])
                                  standings)

        standings-str (->> sorted-standings
                           (map (fn [[_ s]]
                                  (let [pts (+ (* (:wins s) 3) (:draws s))
                                        sg (- (:goals-for s) (:goals-against s))
                                        sg-prefix (if (pos? sg) "+" "")
                                        name-str (pad-end (:name s) name-width)]
                                    (str name-str " " pts " pts (" (:wins s) "V " (:draws s) "E " (:losses s) "D) GP:" (:goals-for s) " SG:" sg-prefix sg))))
                           (str/join "\n"))

        ;; Player Stats
        player-names (into {} (map (juxt :player_id :player_name) team-players))

        stats (reduce (fn [acc e]
                        (let [pid (:player-id e)
                              type (:event-type e)]
                          (update acc pid (fn [current]
                                            (case type
                                              "goal" (update current :goals (fnil inc 0))
                                              "assist" (update current :assists (fnil inc 0))
                                              "own_goal" (update current :own-goals (fnil inc 0))
                                              current)))))
                      {} events)

        goals-conceded (calculate-goalkeeper-goals-conceded matches lineups)

        top-scorers (->> stats
                         (filter (fn [[_ s]] (pos? (or (:goals s) 0))))
                         (sort-by (fn [[pid s]] [(- (or (:goals s) 0)) (get player-names pid "")]))
                         (map (fn [[pid s]] (str (pad-end (get player-names pid "Unknown") name-width) " " (:goals s))))
                         (str/join "\n"))

        top-assisters (->> stats
                           (filter (fn [[_ s]] (pos? (or (:assists s) 0))))
                           (sort-by (fn [[pid s]] [(- (or (:assists s) 0)) (get player-names pid "")]))
                           (map (fn [[pid s]] (str (pad-end (get player-names pid "Unknown") name-width) " " (:assists s))))
                           (str/join "\n"))

        top-gk (->> goals-conceded
                    (sort-by (fn [[pid c]] [c (get player-names pid "")]))
                    (map (fn [[pid c]] (str (pad-end (get player-names pid "Unknown") name-width) " " c)))
                    (str/join "\n"))

        footer (str "\n\nNão esqueçam de votar nos melhores da pelada no app!\n"
                    (generate-voting-link (:id pelada)))]
    (str "```\n"
         title standings-str "\n"
         (if (seq top-scorers) (str "\nGols:\n" top-scorers "\n") "")
         (if (seq top-assisters) (str "\nAssistencias:\n" top-assisters "\n") "")
         (if (seq top-gk) (str "\nGols sofridos:\n" top-gk "\n") "")
         footer
         "\n```")))

(defn generate-vote-ended-message [pelada-id]
  (let [title "🏆 *Ranking da Pelada!* 🏆\n\nA votação encerrou. Os resultados já estão disponíveis!\n\n"
        link (generate-results-link pelada-id)]
    (str title "Confira os destaques no link abaixo:\n" link)))

(defn- format-mention [player]
  (let [name (:player-name player)
        words (str/split (or name "") #"\s+")
        short-name (str/join " " (take 2 words))
        jid (some-> (:phone player) waha/normalize-phone)]
    (if jid
      (str "@" (str/replace jid "@c.us" "") " (" short-name ")")
      short-name)))

(defn- generate-pelada-link [pelada-id]
  (str (get-base-url) "/peladas/" pelada-id))

(defn generate-attendance-reminder [pelada-id pending-players]
  (let [title "⏰ *Lembrete de Presença!* ⏰\n\nAinda temos jogadores com presença pendente para a próxima pelada:\n\n"
        players-str (->> pending-players
                         (map #(str "• " (format-mention %)))
                         (str/join "\n"))]
    (str title players-str "\n\nPor favor, confirmem no app o quanto antes!\n" (generate-pelada-link pelada-id))))

(defn generate-priority-ending-reminder [pelada-id limit-hours pending-players]
  (let [title "⚠️ *Aviso de Encerramento de Prioridade!* ⚠️\n\nA prioridade de confirmação para os mensalistas está prestes a encerrar!\n\n"
        players-str (if (seq pending-players)
                      (str "Mensalistas ainda pendentes:\n"
                           (->> pending-players
                                (map #(str "• " (format-mention %)))
                                (str/join "\n"))
                           "\n\n")
                      "")
        footer (str "Faltam " (or limit-hours "") "h para a pelada. Após esse prazo, novas confirmações de mensalistas irão para a lista de espera como diaristas.\nConfirmem no app o quanto antes!\n" (generate-pelada-link pelada-id))]
    (str title players-str footer)))

(defn generate-vote-reminder [pelada-id pending-voters]
  (let [title "🗳️ *Lembrete de Votação!* 🗳️\n\nAinda faltam alguns jogadores votarem nos melhores da pelada:\n\n"
        players-str (->> pending-voters
                         (map #(str "• " (format-mention %)))
                         (str/join "\n"))]
    (str title players-str "\n\nAcesse o app e deixe seu voto!\n" (generate-voting-link pelada-id))))

(defn generate-matches-results-message [{:keys [matches teams lineups team-players]}]
  (let [title "⚽ *RESULTADOS DAS PARTIDAS*\n\n"
        team-map (into {} (map (juxt :id :name) teams))
        max-name-len (->> teams
                          (map #(count (:name %)))
                          (reduce max 10))
        matches-str (->> matches
                         (map (fn [m]
                                (let [home-name (get team-map (:home-team-id m) "Unknown")
                                      away-name (get team-map (:away-team-id m) "Unknown")
                                      home-score (or (:home-score m) 0)
                                      away-score (or (:away-score m) 0)]
                                  (str (pad-start home-name max-name-len)
                                       "  " home-score " x " away-score "  "
                                       (pad-end away-name max-name-len)))))
                         (str/join "\n"))
        player-names (into {} (map (juxt :player_id :player_name) team-players))
        goals-conceded (calculate-goalkeeper-goals-conceded matches lineups)
        max-p-name-len (->> team-players
                            (map #(count (:player_name %)))
                            (reduce max 0))
        name-width (+ (max 15 (min 30 max-p-name-len)) 2)
        top-gk (->> goals-conceded
                    (sort-by (fn [[pid c]] [c (get player-names pid "")]))
                    (map (fn [[pid c]] (str (pad-end (get player-names pid "Unknown") name-width) " " c)))
                    (str/join "\n"))
        gk-str (if (seq top-gk)
                 (str "\nGols sofridos:\n" top-gk)
                 "")]
    (str title "```\n" matches-str (if (seq gk-str) (str "\n" gk-str) "") "\n```")))

(defn generate-new-pelada-message [pelada-id scheduled-at confirmed-players]
  (let [date-str (format-date scheduled-at)
        title (str "⚽ *Nova Pelada Confirmada!* ⚽\n\nUma nova pelada foi agendada para o dia " date-str ".\n\n"
                   "A lista de presença está aberta! Acesse o app para confirmar ou recusar sua participação:\n"
                   (generate-pelada-link pelada-id) "\n\n")
        confirmations-title "*Confirmados:*\n"
        confirmations-str (if (seq confirmed-players)
                            (->> confirmed-players
                                 (map #(str "• " (format-mention %)))
                                 (str/join "\n"))
                            "Nenhum jogador confirmado ainda.")]
    (str title confirmations-title confirmations-str)))

(defn send-notification!
  "Sends a notification if enabled for the organization."
  [org-id type data db]
  (let [org (db.organization/get-organization org-id db)
        flags (db.organization/get-organization-feature-flags org-id db)
        waha-enabled? (if (nil? flags) true (true? (:waha_communications flags)))]
    (when (and org (:waha-enabled org) waha-enabled?)
      (let [enabled-key (case type
                          :new-pelada :waha-attendance-reminder-enabled
                          :start :waha-start-msg-enabled
                          :end :waha-end-msg-enabled
                          :vote-ended :waha-vote-ended-msg-enabled
                          :attendance-reminder :waha-attendance-reminder-enabled
                          :priority-ending :waha-attendance-reminder-enabled
                          :vote-reminder :waha-vote-reminder-enabled)
            should-send? (or (:force? data) (get org enabled-key))]
        (when should-send?
          (let [message (case type
                          :new-pelada (generate-new-pelada-message (:pelada-id data) (:scheduled-at data) (:confirmed-players data))
                          :start (generate-start-message (:teams data) (:team-players data))
                          :end (generate-end-message data)
                          :vote-ended (generate-vote-ended-message (:pelada-id data))
                          :attendance-reminder (generate-attendance-reminder (:pelada-id data) (:pending-players data))
                          :priority-ending (generate-priority-ending-reminder (:pelada-id data) (:limit-hours data) (:pending-players data))
                          :vote-reminder (generate-vote-reminder (:pelada-id data) (:pending-voters data)))
                mentions (case type
                           :attendance-reminder (->> (:pending-players data)
                                                     (keep #(some-> (:phone %) waha/normalize-phone))
                                                     vec)
                           :priority-ending (->> (:pending-players data)
                                                 (keep #(some-> (:phone %) waha/normalize-phone))
                                                 vec)
                           :vote-reminder (->> (:pending-voters data)
                                               (keep #(some-> (:phone %) waha/normalize-phone))
                                               vec)
                           nil)
                use-all? (:waha-use-all-mention org)
                all-mention? (and (contains? all-mention-types type)
                                  use-all?)
                final-mentions (if all-mention?
                                 (conj mentions "all")
                                 mentions)
                final-message (if all-mention?
                                (str/replace message #"!\*" "! @all*")
                                message)]
            (waha/send-message org final-message final-mentions)
            (when (= type :start)
              (let [team-names (map :name (:teams data))]
                (waha/send-poll org "Quem será o campeão?" team-names false)))
            (when (= type :end)
              (let [results-message (generate-matches-results-message data)]
                (waha/send-message org results-message nil)))))))))
