(ns api-peladaapp.logic.notifications
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.waha :as waha]
   [clojure.string :as str]
   [clojure.tools.logging :as log])
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
  (format (str "%-" length "s") (or s "")))

(defn- pad-start [s length]
  (format (str "%" length "s") (or s "")))

(defn- build-player-name-map [players]
  (into {} (map (fn [p]
                  [(or (:player_id p) (:player-id p) (:id p))
                   (or (:player_name p) (:player-name p) (:name p) (:user-name p))])
                players)))

(defn- format-event-stats [events target-types section-title player-names]
  (let [matching-events (filter (fn [e]
                                  (let [t (some-> (or (:event-type e) (:event_type e)) name str/lower-case)]
                                    (contains? target-types t)))
                                events)
        counts (frequencies (map #(or (:player-id %) (:player_id %)) matching-events))
        sorted (->> counts
                    (sort-by (fn [[pid c]] [(- c) (str/lower-case (or (get player-names pid) ""))]))
                    (map (fn [[pid c]] (str (get player-names pid "Unknown") " " c)))
                    (str/join "\n"))]
    (if (seq sorted)
      (str section-title ":\n" sorted)
      "")))

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

(defn- is-goalkeeper-entry? [x]
  (let [gk-flag (or (:is_goalkeeper x) (:is-goalkeeper x))
        pos (some-> (or (:position x) "") name str/lower-case)]
    (or (= gk-flag true)
        (and (number? gk-flag) (not= gk-flag 0))
        (= pos "goalkeeper"))))

(defn- calculate-goalkeeper-goals-conceded [matches lineups team-players]
  (let [lineups-by-match (group-by #(str (or (:match_id %) (:match-id %) (:id %))) (or lineups []))
        lineup-gk-stats
        (reduce (fn [acc m]
                  (let [m-id (str (or (:id m) (:match_id m) (:match-id m)))
                        home-team-id (str (or (:home-team-id m) (:home_team_id m)))
                        home-score (or (:home-score m) (:home_score m) 0)
                        away-score (or (:away-score m) (:away_score m) 0)
                        lu (get lineups-by-match m-id [])]
                    (reduce (fn [inner-acc l]
                              (if (is-goalkeeper-entry? l)
                                (let [team-id (str (or (:team_id l) (:team-id l)))
                                      player-id (or (:player_id l) (:player-id l))
                                      conceded (if (= team-id home-team-id)
                                                 away-score
                                                 home-score)]
                                  (update inner-acc player-id (fnil + 0) conceded))
                                inner-acc))
                            acc
                            lu)))
                {}
                matches)]
    (if (seq lineup-gk-stats)
      lineup-gk-stats
      (let [gk-players (filter is-goalkeeper-entry? (or team-players []))]
        (reduce (fn [acc p]
                  (let [p-id (or (:player_id p) (:player-id p) (:id p))
                        p-team-id (str (or (:team_id p) (:team-id p)))
                        total-conceded (reduce (fn [c m]
                                                 (let [home-team-id (str (or (:home-team-id m) (:home_team_id m)))
                                                       away-team-id (str (or (:away-team-id m) (:away_team_id m)))
                                                       home-score (or (:home-score m) (:home_score m) 0)
                                                       away-score (or (:away-score m) (:away_score m) 0)]
                                                   (cond
                                                     (= p-team-id home-team-id) (+ c away-score)
                                                     (= p-team-id away-team-id) (+ c home-score)
                                                     :else c)))
                                               0
                                               matches)]
                    (if p-id
                      (assoc acc p-id total-conceded)
                      acc)))
                {}
                gk-players)))))

(defn- update-team-match [acc t-id pts v e d gp gc]
  (-> acc
      (update-in [t-id :pts] + pts)
      (update-in [t-id :v] + v)
      (update-in [t-id :e] + e)
      (update-in [t-id :d] + d)
      (update-in [t-id :gp] + gp)
      (update-in [t-id :gc] + gc)))

(defn- calculate-standings [matches teams]
  (let [initial-stats (into {} (map (fn [t]
                                      [(:id t) {:id (:id t)
                                                :name (:name t)
                                                :pts 0
                                                :v 0
                                                :e 0
                                                :d 0
                                                :gp 0
                                                :gc 0
                                                :sg 0}])
                                    teams))
        updated-stats (reduce
                       (fn [acc m]
                         (let [home-id (or (:home-team-id m) (:home_team_id m))
                               away-id (or (:away-team-id m) (:away_team_id m))
                               home-score (or (:home-score m) (:home_score m) 0)
                               away-score (or (:away-score m) (:away_score m) 0)]
                           (if (and (get acc home-id) (get acc away-id))
                             (cond
                               (> home-score away-score)
                               (-> acc
                                   (update-team-match home-id 3 1 0 0 home-score away-score)
                                   (update-team-match away-id 0 0 0 1 away-score home-score))

                               (< home-score away-score)
                               (-> acc
                                   (update-team-match away-id 3 1 0 0 away-score home-score)
                                   (update-team-match home-id 0 0 0 1 home-score away-score))

                               :else
                               (-> acc
                                   (update-team-match home-id 1 0 1 0 home-score away-score)
                                   (update-team-match away-id 1 0 1 0 away-score home-score)))
                             acc)))
                       initial-stats
                       matches)]
    (->> updated-stats
         vals
         (map (fn [s] (assoc s :sg (- (:gp s) (:gc s)))))
         (sort-by (fn [s] [(- (:pts s))
                           (- (:v s))
                           (- (:sg s))
                           (- (:gp s))
                           (str/lower-case (or (:name s) ""))])))))

(defn generate-end-message [{:keys [matches teams events team-players pelada pelada-id] :as data}]
  (let [pelada-obj (or pelada (when (map? data) (:pelada data)))
        p-id (or pelada-id (:pelada-id data) (:id pelada-obj))
        scheduled-at (or (:scheduled-at pelada-obj) (:scheduled_at pelada-obj) (:scheduled-at data))
        date-str (format-date scheduled-at)
        header (str "Resumo da rodada" (if (seq date-str) (str " " date-str) ""))

        standings (calculate-standings matches teams)
        max-team-len (if (seq teams)
                       (reduce max 0 (map #(count (:name %)) teams))
                       0)
        name-width (max 15 (+ 2 max-team-len))
        standings-str (->> standings
                           (map (fn [s]
                                  (let [name-str (pad-end (:name s) name-width)
                                        pts-str (pad-start (str (:pts s)) 2)
                                        sg-val (:sg s)
                                        sg-str (if (pos? sg-val) (str "+" sg-val) (str sg-val))]
                                    (str name-str pts-str " pts (" (:v s) "V " (:e s) "E " (:d s) "D) GP:" (:gp s) " SG:" sg-str))))
                           (str/join "\n"))
        classificacao-block (if (seq standings-str)
                              (str "Classificacao:\n" standings-str)
                              "")

        player-names (build-player-name-map team-players)
        goals-block (format-event-stats events #{"goal" "gol"} "Gols" player-names)
        assists-block (format-event-stats events #{"assist" "assistencia"} "Assistencias" player-names)

        body-sections (remove str/blank? [classificacao-block goals-block assists-block])
        body-str (str/join "\n\n" body-sections)
        code-content (str header (if (seq body-str) (str "\n\n" body-str) ""))
        voting-link (when (some? p-id) (generate-voting-link p-id))
        footer (when voting-link
                 (str "\n\n🗳️ *Votação Aberta!*\nAcesse o link para votar nos melhores da pelada:\n" voting-link))]
    (str "```\n" code-content "\n```" (or footer ""))))

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
        footer (str "A prioridade de confirmação para mensalistas encerra em breve (prazo de " (or limit-hours "") "h antes da pelada). Após esse prazo, novas confirmações de mensalistas irão para a lista de espera como diaristas.\nConfirmem no app o quanto antes!\n" (generate-pelada-link pelada-id))]
    (str title players-str footer)))

(defn generate-vote-reminder [pelada-id pending-voters]
  (let [title "🗳️ *Lembrete de Votação!* 🗳️\n\nAinda faltam alguns jogadores votarem nos melhores da pelada:\n\n"
        players-str (->> pending-voters
                         (map #(str "• " (format-mention %)))
                         (str/join "\n"))]
    (str title players-str "\n\nAcesse o app e deixe seu voto!\n" (generate-voting-link pelada-id))))

(defn generate-casual-player-open-message [pelada-id scheduled-at]
  (let [date-str (format-date scheduled-at)
        title (str "⚽ *Lista de Presença Aberta!* ⚽\n\n"
                   (if (seq date-str) (str "A lista de presença para a pelada do dia " date-str " está aberta!\n") "A lista de presença para a próxima pelada está aberta!\n")
                   "Acesse o app para confirmar sua participação:\n"
                   (generate-pelada-link pelada-id))]
    title))

(defn generate-casual-player-priority-ended-message [pelada-id scheduled-at limit-hours]
  (let [date-str (format-date scheduled-at)
        limit-info (if limit-hours (str "Prazo de " limit-hours "h atingido. ") "")
        title (str "⚠️ *Prioridade de Mensalistas Encerrada!* ⚠️\n\n"
                   (if (seq date-str) (str "A prioridade para mensalistas da pelada do dia " date-str " encerrou. ") "A prioridade para mensalistas encerrou. ")
                   limit-info
                   "A lista de presença está agora liberada para diaristas e convidados!\n"
                   "Acesse o app para garantir sua vaga:\n"
                   (generate-pelada-link pelada-id))]
    title))

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
        player-names (build-player-name-map team-players)
        goals-conceded (calculate-goalkeeper-goals-conceded matches lineups team-players)
        top-gk (->> goals-conceded
                    (sort-by (fn [[pid c]] [c (str/lower-case (or (get player-names pid) ""))]))
                    (map (fn [[pid c]] (str (get player-names pid "Unknown") " " c)))
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

(defn- pelada-full? [pelada-id db]
  (when-let [pelada (db.pelada/get-pelada pelada-id db)]
    (let [max-p (or (:max-players pelada) (get pelada :max_players))
          num-teams (:num-teams pelada (get pelada :num_teams))
          players-per-team (:players-per-team pelada (get pelada :players_per_team))
          max-cap (or (when (and (number? max-p) (pos? max-p)) max-p)
                      (when (and (number? num-teams) (pos? num-teams)
                                 (number? players-per-team) (pos? players-per-team))
                        (* num-teams players-per-team)))]
      (if (and (number? max-cap) (pos? max-cap))
        (let [confirmed-count (count (db.attendance/list-confirmed-players-by-pelada pelada-id db))]
          (>= confirmed-count max-cap))
        false))))

(defn- send-private-casual-player-notifications!
  [org type data db]
  (try
    (let [org-id (:id org)
          pelada-id (:pelada-id data)
          notify-casual? (if (contains? data :notify-casual-players)
                           (boolean (:notify-casual-players data))
                           true)]
      (when (and pelada-id
                 (case type
                   :new-pelada notify-casual?
                   :casual-priority-ended true
                   false))
        (when-not (pelada-full? pelada-id db)
          (let [opted-in-users (db.user/list-opted-in-casual-users-to-notify-for-pelada org-id pelada-id db)]
            (doseq [u opted-in-users]
              (when-let [jid (some-> (:phone u) waha/normalize-phone)]
                (let [msg (case type
                            :new-pelada (generate-casual-player-open-message pelada-id (:scheduled-at data))
                            :casual-priority-ended (generate-casual-player-priority-ended-message pelada-id (:scheduled-at data) (:limit-hours data))
                            nil)]
                  (when msg
                    (waha/send-message (assoc org :waha-group-id jid) msg nil)))))))))
    (catch Exception e
      (log/error e "Failed to send private notifications to casual players:"))))

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
                          :casual-priority-ended :waha-attendance-reminder-enabled
                          :vote-reminder :waha-vote-reminder-enabled)
            should-send? (or (:force? data) (get org enabled-key))]
        (when should-send?
          (when-not (= type :casual-priority-ended)
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
                  (waha/send-message org results-message nil)))))
          (send-private-casual-player-notifications! org type data db))))))
