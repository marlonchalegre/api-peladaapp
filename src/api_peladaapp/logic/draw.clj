(ns api-peladaapp.logic.draw
  "Entry point for drawing the teams of a pelada.

   Three algorithms share this entry point:

     :classic — the original balance-by-grade shuffle, unchanged;
     :gemini  — cost-driven chemistry draw (logic.draw-gemini);
     :gpt     — constraint-first tactical draw (logic.draw-gpt).

   The two new ones read the confirmed attendance of the pelada, never the
   whole roster, and return the evidence behind the division so the screen can
   explain itself."
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.draw-common :as common]
   [api-peladaapp.logic.draw-gemini :as draw.gemini]
   [api-peladaapp.logic.draw-gpt :as draw.gpt]
   [api-peladaapp.logic.draw-history :as draw.history]
   [api-peladaapp.logic.randomize :as logic.randomize]
   [clojure.math :as math]
   [clojure.tools.logging :as log]
   [next.jdbc :as jdbc]))

(def default-algorithm
  "The draw the group already knows; anything unrecognised falls back to it."
  "classic")

(def ^:private draw-fns
  {draw.gemini/algorithm-name draw.gemini/draw
   draw.gpt/algorithm-name draw.gpt/draw})

(def algorithms (into #{default-algorithm} (keys draw-fns)))

(defn- draw-cutoff
  "Peladas closed strictly before this instant feed the history. Falls back to
   now when the pelada has no date, so a draw never reads its own night."
  [pelada]
  (let [scheduled (:scheduled-at pelada)]
    (if scheduled
      (helpers.time/to-utc-timestamp-str scheduled)
      (helpers.time/to-utc-timestamp-str (java.time.Instant/now)))))

(defn- capacity-split
  "Teams hold `players-per-team` each. Anyone beyond that stays out of the
   division, strongest first, and is reported so nobody disappears silently.

   A pelada with no squad size set (or zero) spreads everyone across the teams
   instead of benching the whole list."
  [players num-teams players-per-team]
  (let [per-team (if (and players-per-team (pos? players-per-team))
                   players-per-team
                   (long (math/ceil (/ (double (count players)) (max 1 num-teams)))))
        capacity (* num-teams per-team)
        ordered (common/strongest-first players)]
    (if (<= (count ordered) capacity)
      [(vec ordered) []]
      [(vec (take capacity ordered)) (vec (drop capacity ordered))])))

(def ^:private fallback-grade
  "Used only when a squad has no graded player at all."
  7.0)

(defn- fill-missing-grades
  "A player with no grade recorded gets a provisional one — the average of the
   others in the same position, or of the whole squad — and is flagged so the
   report can say the number was not theirs.

   Without this the algorithms disagree: the tactical draw would read a missing
   grade as zero and bury the player, the chemistry draw would read it as 7.0."
  [players]
  (let [graded (filter :grade players)
        squad-mean (when (seq graded) (common/mean (map :grade graded)))
        by-position (->> graded
                         (group-by #(common/normalize-position (:position %)))
                         (reduce-kv (fn [m position group]
                                      (assoc m position (common/mean (map :grade group))))
                                    {}))]
    (mapv (fn [player]
            (if (:grade player)
              player
              (assoc player
                     :grade (or (get by-position (common/normalize-position (:position player)))
                                squad-mean
                                fallback-grade)
                     :provisional-grade true)))
          players)))

(defn- persist-assignments!
  [pelada-id teams team-ids tx]
  (db.team/clear-teams-players pelada-id tx)
  (let [assignments (for [[idx team] (map-indexed vector teams)
                          player team]
                      {:team_id (nth team-ids idx)
                       :player_id (:id player)
                       :is_goalkeeper false})]
    (when (seq assignments)
      (db.team/add-team-players-batch! (vec assignments) tx))
    (count assignments)))

(defn- enrich-players
  [player-ids org-id db]
  (mapv (fn [p] {:id (:id p)
                 :name (or (:name p) (str (:id p)))
                 :position (:position p)
                 :grade (some-> (:grade p) double)})
        (db.player/get-players-details-for-balance (vec player-ids) org-id db)))

(defn- bad-request!
  [message]
  (throw (ex-info message {:type :bad-request :message message})))

(defn draw-teams!
  "Draws the teams of `pelada-id` and writes the result.

   Options: :algorithm (\"classic\" | \"gemini\" | \"gpt\"), :use-history,
   :player-ids (fallback pool) and :players-per-team.

   Returns a report map for the caller to hand back to the UI. The classic
   algorithm reports nothing beyond its name — it has no justifications."
  [pelada-id {:keys [algorithm use-history player-ids players-per-team]} db]
  (let [algorithm (or (algorithms algorithm) default-algorithm)]
    (if (= default-algorithm algorithm)
      (do (logic.randomize/randomize-teams! pelada-id player-ids players-per-team db)
          {:algorithm default-algorithm :justification nil})
      (jdbc/with-transaction [tx db]
        (let [pelada (db.pelada/get-pelada pelada-id tx)
              org-id (:organization-id pelada)
              teams (db.team/list-pelada-teams pelada-id tx)
              _ (when (empty? teams) (bad-request! "Pelada has no teams to fill"))
              fixed-gks (set (keep identity [(:home-fixed-goalkeeper-id pelada)
                                             (:away-fixed-goalkeeper-id pelada)]))
              confirmed (map :player-id (db.attendance/list-confirmed-players-by-pelada pelada-id tx))
              pool (remove fixed-gks (if (seq confirmed) confirmed player-ids))
              players (fill-missing-grades (enrich-players pool org-id tx))
              [selected benched] (capacity-split players (count teams) players-per-team)
              _ (when (empty? selected) (bad-request! "No confirmed players to draw"))
              signals (draw.history/build org-id (draw-cutoff pelada) tx)
              result ((get draw-fns algorithm) selected (mapv :name teams)
                                               {:use-history (boolean use-history) :history-signals signals})
              ;; Report order follows the board order, so team N in the
              ;; justification is the team named N on screen.
              ordered (mapv (comp #(mapv (fn [p] (select-keys p [:id])) %) :players)
                            (:teams result))
              assigned (persist-assignments! pelada-id ordered (mapv :id teams) tx)]
          (log/info (str "[DRAW] " algorithm " placed " assigned " players in "
                         (count teams) " teams of pelada " pelada-id))
          {:algorithm algorithm
           :justification (assoc result
                                 :benched (mapv (fn [p] {:name (:name p)
                                                         :grade (common/round2 (:grade p))})
                                                benched)
                                 :use_history (boolean use-history)
                                 :players_considered (count selected)
                                 :source (if (seq confirmed) "confirmed_attendance" "board"))})))))
