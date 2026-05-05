(ns api-peladaapp.logic.notifications
  (:require
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.logic.waha :as waha]
   [clojure.string :as str])
  (:import
   [java.time Instant ZoneId]
   [java.time.format DateTimeFormatter]))

(def position-order
  {"goalkeeper" 0
   "defender" 1
   "midfielder" 2
   "striker" 3})

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

(defn- get-base-url []
  (or (System/getenv "FRONTEND_URL") "http://localhost:5173"))

(defn- generate-voting-link [pelada-id]
  (str (get-base-url) "/peladas/" pelada-id "/voting"))

(defn- generate-results-link [pelada-id]
  (str (get-base-url) "/peladas/" pelada-id "/results"))

(defn generate-start-message [teams team-players]
  (let [title "*ESCALAÇÃO DA PELADA*\n\n"
        teams-grouped (group-by :team_id team-players)

        ;; Calculate max name length for alignment (min 15, max 30) + 2
        max-name-len (->> team-players
                          (map #(count (:player_name %)))
                          (reduce max 0))
        name-width (+ (max 15 (min 30 max-name-len)) 2)

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
                                (str "*" (str/upper-case (:name team)) "*\n" players-str))))
                       (str/join "\n\n"))]
    (str "```\n" title teams-str "\n```")))

(defn- format-date [date-str]
  (try
    (let [instant (Instant/parse date-str)
          formatter (-> (DateTimeFormatter/ofPattern "dd/MM")
                        (.withZone (ZoneId/of "America/Sao_Paulo")))]
      (.format formatter instant))
    (catch Exception _ "")))

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

        ;; Goals Conceded (Goalkeepers)
        goals-conceded (reduce (fn [acc m]
                                 (let [lu (filter #(= (:match_id m) (:match_id %)) lineups)]
                                   (reduce (fn [inner-acc l]
                                             (if (not= 0 (:is_goalkeeper l))
                                               (let [conceded (if (= (:team_id l) (:home-team-id m))
                                                                (or (:away-score m) 0)
                                                                (or (:home-score m) 0))]
                                                 (update inner-acc (:player_id l) (fnil + 0) conceded))
                                               inner-acc))
                                           acc lu)))
                               {} matches)

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
  (if (:phone player)
    (str "@" (:player-name player))
    (:player-name player)))

(defn generate-attendance-reminder [pending-players]
  (let [title "⏰ *Lembrete de Presença!* ⏰\n\nAinda temos jogadores com presença pendente para a próxima pelada:\n\n"
        players-str (->> pending-players
                         (map #(str "• " (format-mention %)))
                         (str/join "\n"))]
    (str title players-str "\n\nPor favor, confirmem no app o quanto antes!")))

(defn generate-vote-reminder [pelada-id pending-voters]
  (let [title "🗳️ *Lembrete de Votação!* 🗳️\n\nAinda faltam alguns jogadores votarem nos melhores da pelada:\n\n"
        players-str (->> pending-voters
                         (map #(str "• " (format-mention %)))
                         (str/join "\n"))]
    (str title players-str "\n\nAcesse o app e deixe seu voto!\n" (generate-voting-link pelada-id))))

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
                          :end (generate-end-message data)
                          :vote-ended (generate-vote-ended-message (:pelada-id data))
                          :attendance-reminder (generate-attendance-reminder (:pending-players data))
                          :vote-reminder (generate-vote-reminder (:pelada-id data) (:pending-voters data)))
                mentions (case type
                           :attendance-reminder (->> (:pending-players data)
                                                     (keep #(some-> (:phone %) waha/normalize-phone)))
                           :vote-reminder (->> (:pending-voters data)
                                               (keep #(some-> (:phone %) waha/normalize-phone)))
                           nil)
                use-all? (:waha-use-all-mention org)
                final-mentions (if (and (contains? #{:attendance-reminder :vote-reminder} type)
                                        use-all?)
                                 (conj (vec mentions) "all")
                                 mentions)
                final-message (if (and (contains? #{:attendance-reminder :vote-reminder} type)
                                       use-all?)
                                (str/replace message #"!\*" "! @all*")
                                message)]
            (waha/send-message org final-message final-mentions)))))))
