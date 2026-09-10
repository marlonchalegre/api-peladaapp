(ns api-peladaapp.logic.draw-history
  "Historical signals shared by the team draw algorithms.

   These are conservative, auditable preferences — how often two players have
   been teammates, how titles are spread, which assists actually connect two
   names — and never a prediction of who will win. Nights that cannot be read
   without guessing (missing rosters, unresolved ties, assists with no explicit
   link to a goal) are counted in `:coverage` and left out of the signals."
  (:require
   [api-peladaapp.db.draw-history :as db.draw-history]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.logic.draw-common :as common]
   [clojure.set :as set]
   [clojure.string :as str]))

(def ^:private excluded-names
  "Placeholder rosters that stand for a shirt, not for a person."
  #{"Goleiro Azul" "Goleiro Laranja" "MCP for Testing Only"})

(defn duo
  "Order-independent key for a pair of players."
  [a b]
  (vec (sort [a b])))

(defn- bump
  ([m k] (bump m k 1))
  ([m k n] (update m k (fnil + 0) n)))

(defn- bump-all
  ([m ks] (reduce bump m ks))
  ([m ks n] (reduce #(bump %1 %2 n) m ks)))

(defn- standings
  "Points, wins, goal difference and goals for — the same ordering the results
   screen uses, so a title here means the same thing it means in the app."
  [team-ids matches]
  (reduce (fn [table {:keys [home_team_id away_team_id home_score away_score]}]
            (let [hs (or home_score 0)
                  as (or away_score 0)]
              (-> table
                  (update home_team_id
                          (fn [[pts wins gd gf]]
                            [(+ pts (cond (> hs as) 3 (= hs as) 1 :else 0))
                             (+ wins (if (> hs as) 1 0))
                             (+ gd (- hs as))
                             (+ gf hs)]))
                  (update away_team_id
                          (fn [[pts wins gd gf]]
                            [(+ pts (cond (> as hs) 3 (= as hs) 1 :else 0))
                             (+ wins (if (> as hs) 1 0))
                             (+ gd (- as hs))
                             (+ gf as)])))))
          (zipmap team-ids (repeat [0 0 0 0]))
          matches))

(defn- champion
  "The single team on top, or nil when the criteria leave a tie unresolved.
   A tie is reported rather than broken by row order. Vectors compare
   lexicographically, which is exactly the points/wins/diff/goals ordering."
  [team-ids matches]
  (let [table (standings team-ids matches)
        best (last (sort (map (comp vec table) team-ids)))
        winners (filter #(= (vec (table %)) best) team-ids)]
    (when (= 1 (count winners))
      (first winners))))

(defn- process-night
  [history {:keys [pelada-id team-rosters matches unfinished? num-teams]}]
  (let [team-ids (set (keys team-rosters))
        flat (mapcat val team-rosters)
        distinct-flat (set flat)
        team-of (reduce-kv (fn [m t roster] (into m (map (fn [p] [p t])) roster)) {} team-rosters)
        all-pairs (delay (common/pairs distinct-flat))
        team-pairs (delay (vec (mapcat (comp common/pairs val) team-rosters)))
        invalid? (or (< num-teams 2)
                     (empty? matches)
                     (some (comp empty? val) team-rosters)
                     (not= (count flat) (count distinct-flat)))
        ;; A match pointing at a team with no readable roster cannot be scored.
        foreign-match? (some (fn [{:keys [home_team_id away_team_id]}]
                               (or (not (team-ids home_team_id))
                                   (not (team-ids away_team_id))
                                   (= home_team_id away_team_id)))
                             matches)]
    (cond
      invalid?
      (update-in history [:coverage :nights-skipped-invalid-rosters-or-no-games] (fnil inc 0))

      foreign-match?
      (update-in history [:coverage :nights-skipped-invalid-match-teams] (fnil inc 0))

      :else
      (let [with-rosters
            (-> history
                (update :coverage update :roster-nights-used (fnil inc 0))
                (update :nights bump-all distinct-flat)
                (update :available bump-all @all-pairs)
                (update :together bump-all @team-pairs)
                (update :opposite
                        (fn [m]
                          (reduce (fn [acc [a b]]
                                    (if (= (team-of a) (team-of b)) acc (bump acc [a b])))
                                  m
                                  @all-pairs)))
                (update :nights-played
                        (fn [m] (reduce (fn [acc p] (update acc p (fnil conj []) pelada-id)) m distinct-flat))))]
        (if unfinished?
          (update-in with-rosters [:coverage :titles-skipped-unfinished-matches] (fnil inc 0))
          (if-let [winner (champion team-ids matches)]
            (let [expected (/ 1.0 num-teams)
                  champ-roster (get team-rosters winner)]
              (-> with-rosters
                  (update :coverage update :title-nights-used (fnil inc 0))
                  (update :title-opportunities bump-all distinct-flat)
                  (update :expected-titles bump-all distinct-flat expected)
                  (update :pair-title-opportunities bump-all @team-pairs)
                  (update :expected-pair-titles bump-all @team-pairs expected)
                  (update :titles bump-all champ-roster)
                  (update :pair-titles bump-all (common/pairs champ-roster))
                  (update :champions assoc pelada-id champ-roster)))
            (update-in with-rosters [:coverage :titles-skipped-unresolved-ties] (fnil inc 0))))))))

(defn- add-lineup-signals
  "On-pitch pairings, taken from per-match lineups rather than the night roster,
   so loans and mid-night changes do not invent partnerships."
  [history lineups-by-match matches]
  (reduce (fn [acc {:keys [id home_team_id away_team_id home_score away_score]}]
            (let [home (get lineups-by-match [id home_team_id] #{})
                  away (get lineups-by-match [id away_team_id] #{})]
              (if (or (empty? home) (empty? away) (seq (set/intersection home away)))
                (update-in acc [:coverage :matches-skipped-missing-or-ambiguous-lineups] (fnil inc 0))
                (let [hs (or home_score 0)
                      as (or away_score 0)]
                  (-> acc
                      (update :coverage update :matches-with-usable-lineups (fnil inc 0))
                      (update :valid-lineups assoc id [home away])
                      (update :matches-together bump-all (concat (common/pairs home) (common/pairs away)))
                      (update :wins-together bump-all
                              (cond (> hs as) (common/pairs home)
                                    (> as hs) (common/pairs away)
                                    :else [])))))))
          history
          matches))

(defn- add-assist-signals
  "Only assists explicitly linked to a goal and confirmed by a shared lineup.
   The same pass is never counted twice through duplicated rows."
  [history links total-assist-events]
  (let [result (reduce (fn [acc {:keys [match_id passer_id scorer_id goal_id]}]
                         (let [sides (get-in acc [:valid-lineups match_id])
                               teammates? (and sides
                                               (some (fn [side] (and (side passer_id) (side scorer_id))) sides))
                               identity-key [passer_id goal_id]]
                           (cond
                             (or (= passer_id scorer_id) (not teammates?))
                             (update-in acc [:coverage :assists-ignored-unverified-teammates] (fnil inc 0))

                             (contains? (:seen-assists acc) identity-key)
                             (update-in acc [:coverage :assists-ignored-duplicates] (fnil inc 0))

                             :else
                             (-> acc
                                 (update :seen-assists conj identity-key)
                                 (update :assists bump [passer_id scorer_id])
                                 (update :coverage update :explicit-assists-used (fnil inc 0))))))
                       (assoc history :seen-assists #{})
                       links)]
    (-> result
        (dissoc :seen-assists :valid-lineups)
        (assoc-in [:coverage :assist-events] total-assist-events)
        (assoc-in [:coverage :assists-ignored-without-explicit-link]
                  (max 0 (- total-assist-events (count links)))))))

(defn- add-match-load
  "Minutes-on-the-night proxy: every finished match counts for the whole team
   registered that night, together with the goals that team conceded in it.
   Used by the offensive and defensive indices."
  [history roster-by-team matches]
  (reduce (fn [acc {:keys [home_team_id away_team_id home_score away_score]}]
            (let [home (get roster-by-team home_team_id #{})
                  away (get roster-by-team away_team_id #{})]
              (-> acc
                  (update :matches-played bump-all home)
                  (update :matches-played bump-all away)
                  (update :goals-conceded bump-all home (or away_score 0))
                  (update :goals-conceded bump-all away (or home_score 0)))))
          history
          matches))

(defn- add-scoring
  [history events]
  (reduce (fn [acc {:keys [player_id event_type count]}]
            (let [kind (if (= "goal" (some-> event_type name)) :goals :assist-count)]
              (update acc kind bump player_id (or count 0))))
          history
          events))

(defn- add-votes
  "Star totals per player plus the organization mean, the prior of the Bayesian
   rating that keeps a player with three votes from topping the list."
  [history vote-totals]
  (let [total (reduce + 0 (keep :total vote-totals))
        counted (reduce + 0 (keep :count vote-totals))]
    (-> history
        (assoc :vote-mean (if (pos? counted) (/ (double total) counted) 3.5))
        (assoc :vote-stars
               (into {} (map (juxt :player_id #(hash-map :sum (or (:total %) 0)
                                                         :count (or (:count %) 0))))
                     vote-totals)))))

(defn- add-droughts
  "Peladas each player has played since the last one they won. Never a champion
   means every night they played counts."
  [history peladas]
  (let [ordered (map :id peladas)
        champions (:champions history)
        played (:nights-played history)]
    (assoc history :drought
           (into {}
                 (map (fn [[player their-nights]]
                        (let [played-set (set their-nights)
                              relevant (filter played-set ordered)
                              last-title (last (keep-indexed
                                                (fn [idx pelada-id]
                                                  (when (contains? (get champions pelada-id #{}) player) idx))
                                                relevant))]
                          [player (if last-title
                                    (- (count relevant) (inc last-title))
                                    (count relevant))])))
                 played))))

(def empty-history
  {:names {} :positions {} :grades {}
   :nights {} :nights-played {} :titles {} :title-opportunities {} :expected-titles {}
   :together {} :available {} :opposite {}
   :pair-titles {} :pair-title-opportunities {} :expected-pair-titles {}
   :matches-together {} :wins-together {} :assists {} :valid-lineups {}
   :champions {} :drought {} :coverage {} :before nil
   :matches-played {} :goals-conceded {} :goals {} :assist-count {}
   :vote-stars {} :vote-mean 3.5})

(defn build
  "Historical signals for `organization-id`, restricted to peladas closed before
   `before` (an ISO date-time string). Returns the map described in this ns."
  [organization-id before db]
  (let [players (db.player/list-players-for-balance organization-id db)
        eligible (remove #(contains? excluded-names (some-> (:name %) str/trim)) players)
        peladas (db.draw-history/list-closed-peladas organization-id before db)
        rosters (db.draw-history/list-team-rosters organization-id before db)
        matches (db.draw-history/list-finished-matches organization-id before db)
        unfinished (->> (db.draw-history/count-unfinished-matches-by-pelada organization-id before db)
                        (map :pelada_id)
                        set)
        lineups (db.draw-history/list-match-lineups organization-id before db)
        links (db.draw-history/list-assist-links organization-id before db)
        assist-events (db.draw-history/count-assist-events organization-id before db)
        scoring (db.draw-history/list-scoring-events organization-id before db)
        vote-totals (db.draw-history/aggregate-vote-stars organization-id before db)
        known (set (map :id eligible))
        rosters-by-pelada (->> rosters
                               (filter #(known (:player_id %)))
                               (group-by :pelada_id))
        matches-by-pelada (group-by :pelada_id matches)
        lineups-by-match (->> lineups
                              (filter #(known (:player_id %)))
                              (reduce (fn [acc {:keys [match_id team_id player_id]}]
                                        (update acc [match_id team_id] (fnil conj #{}) player_id))
                                      {}))
        base (assoc empty-history
                    :before before
                    :names (into {} (map (juxt :id #(some-> (:name %) str/trim))) eligible)
                    :positions (into {} (map (juxt :id :position)) eligible)
                    :grades (into {} (map (juxt :id #(some-> (:grade %) double))) eligible))
        with-nights (reduce (fn [acc {:keys [id]}]
                              (let [team-rosters (->> (get rosters-by-pelada id)
                                                      (group-by :team_id)
                                                      (reduce-kv (fn [m t rows]
                                                                   (assoc m t (set (map :player_id rows))))
                                                                 {}))]
                                (process-night acc {:pelada-id id
                                                    :team-rosters team-rosters
                                                    :matches (get matches-by-pelada id)
                                                    :unfinished? (contains? unfinished id)
                                                    :num-teams (count team-rosters)})))
                            (assoc-in base [:coverage :closed-nights-before-draw] (count peladas))
                            peladas)]
    (-> with-nights
        (add-lineup-signals lineups-by-match matches)
        (add-assist-signals (filter #(and (known (:passer_id %)) (known (:scorer_id %))) links)
                            assist-events)
        (add-match-load (->> rosters
                             (filter #(known (:player_id %)))
                             (reduce (fn [acc {:keys [team_id player_id]}]
                                       (update acc team_id (fnil conj #{}) player_id))
                                     {}))
                        matches)
        (add-scoring (filter #(known (:player_id %)) scoring))
        (add-votes (filter #(known (:player_id %)) vote-totals))
        (add-droughts peladas))))

(def weights
  "Relative influence of each signal inside the [0, 1] history penalty."
  {:repeat-partners 0.35
   :player-titles 0.20
   :pair-titles 0.20
   :assist-connections 0.25})

(defn player-effect
  "How much a player's titles exceed what the nights they played would give
   anyone. Smoothed by five nights so a short history stays near zero."
  [history player]
  (let [titles (get-in history [:titles player] 0)
        expected (get-in history [:expected-titles player] 0.0)
        opportunities (get-in history [:title-opportunities player] 0)]
    (/ (- titles expected) (+ opportunities 5.0))))

(defn pair-features
  [history a b]
  (let [key (duo a b)
        together (get-in history [:together key] 0)
        available (get-in history [:available key] 0)
        opportunities (get-in history [:pair-title-opportunities key] 0)
        titles-together (get-in history [:pair-titles key] 0)
        expected (+ (get-in history [:expected-pair-titles key] 0.0)
                    (* opportunities (/ (+ (player-effect history a) (player-effect history b)) 2.0)))
        matches (get-in history [:matches-together key] 0)
        links (+ (get-in history [:assists [a b]] 0)
                 (get-in history [:assists [b a]] 0))]
    {:players [(get-in history [:names a] (str a)) (get-in history [:names b] (str b))]
     :nights_together together
     :nights_both_present available
     :titles_together titles-together
     :title_opportunities opportunities
     :matches_together matches
     :wins_together (get-in history [:wins-together key] 0)
     :assists_first_to_second (get-in history [:assists [a b]] 0)
     :assists_second_to_first (get-in history [:assists [b a]] 0)
     ;; Smoothing denominators keep rarely-seen duos close to neutral.
     :repeat_score (/ (double together) (+ available 4.0))
     :title_effect (/ (- titles-together expected) (+ opportunities 8.0))
     :assist_strength (/ (double links) (+ matches links 8.0))}))

(defn team-score
  "The four aggregates the penalty is built from. This is what the search runs
   on, so it deliberately allocates nothing per player or per pair beyond the
   numbers themselves."
  [history players]
  (let [duos (map (fn [[a b]] (pair-features history a b)) (common/pairs players))]
    {:player_title_effect (common/mean (map #(player-effect history %) players))
     :pair_title_effect (common/mean (map :title_effect duos))
     :repeat_score (common/mean (map :repeat_score duos))
     :assist_strength (common/mean (map :assist_strength duos))}))

(defn team-features
  "`team-score` plus the per-player and per-pair evidence the report shows.
   Only the report path needs this; the search must not cache it."
  [history players]
  (assoc (team-score history players)
         :players (mapv (fn [p] {:name (get-in history [:names p] (str p))
                                 :nights (get-in history [:nights p] 0)
                                 :titles (get-in history [:titles p] 0)
                                 :title_opportunities (get-in history [:title-opportunities p] 0)})
                        (sort players))
         :pairs (vec (map (fn [[a b]] (pair-features history a b)) (common/pairs players)))))

(defn- evaluate*
  [history teams details? team-features-fn]
  (let [values (map team-features-fn teams)
        components {:repeat-partners (common/mean (map :repeat_score values))
                    :player-titles (min 1.0 (/ (common/spread (map :player_title_effect values)) 2.0))
                    :pair-titles (min 1.0 (/ (common/spread (map :pair_title_effect values)) 2.0))
                    :assist-connections (if (seq (:assists history))
                                          (/ (+ (- 1.0 (common/mean (map :assist_strength values)))
                                                (common/spread (map :assist_strength values)))
                                             2.0)
                                          0.0)}
        penalty (reduce-kv (fn [total k v] (+ total (* (get weights k) v))) 0.0 components)]
    (cond-> {:penalty penalty :components components}
      details? (assoc :teams (vec values) :weights weights))))

(defn scorer
  "An `evaluate` closure that remembers the score of every team composition it
   has already seen. The searches try tens of thousands of divisions built from
   a handful of distinct teams, so this is where the time goes.

   It caches `team-score`, never `team-features`: the report maps are hundreds
   of times larger and the search never reads them."
  [history]
  (let [cache (atom {})
        score (fn [players]
                (let [key (set players)]
                  (or (get @cache key)
                      (let [value (team-score history players)]
                        (swap! cache assoc key value)
                        value))))]
    (fn [teams] (evaluate* history teams false score))))

(defn evaluate
  "Uncached penalty for a single division. Use `scorer` inside a search."
  ([history teams] (evaluate* history teams false #(team-score history %)))
  ([history teams details?]
   (evaluate* history teams details? (if details?
                                       #(team-features history %)
                                       #(team-score history %)))))

(defn short-history-players
  "Players with fewer than `threshold` nights on a registered roster. Little
   history is not a statement about skill — it only means fewer observations."
  [history players threshold]
  (set (filter #(< (get-in history [:nights %] 0) threshold) players)))
