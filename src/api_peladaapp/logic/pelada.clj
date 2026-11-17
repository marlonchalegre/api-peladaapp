(ns api-peladaapp.logic.pelada
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.pelada-summary :as pelada-summary]
   [api-peladaapp.logic.schedule :as schedule]
   [api-peladaapp.logic.vote :as logic.vote]
   [schema.core :as s]))

(defn ensure-open
  "Ensure pelada can start. Returns pelada or throws with :bad-request."
  [pelada]
  (case (:status pelada)
    "open" pelada
    "attendance" (throw (ex-info "Attendance list is still open. Close it before starting the pelada." {:type :bad-request
                                                                                                        :message "Attendance list is still open. Close it before starting the pelada."}))
    (throw (ex-info "Pelada already started or closed" {:type :bad-request
                                                        :message "Pelada already started or closed"}))))

(defn ensure-running
  "Ensure pelada is currently running. Returns pelada or throws with :bad-request.
   Optional :allow-closed? allows closed/voting status."
  ([pelada] (ensure-running pelada {}))
  ([pelada opts]
   (let [status (:status pelada)
         allowed? (or (= "running" status)
                      (and (:allow-closed? opts)
                           (contains? #{"closed" "voting"} status)))]
     (if allowed?
       pelada
       (throw (ex-info "Pelada is not running"
                       {:type :bad-request
                        :message (str "Action only allowed while pelada is running. Current status: " status)}))))))

(defn ensure-schedulable-team-count
  "Ensure there are enough teams for the selected pelada format. Returns team ids."
  ([team-ids] (ensure-schedulable-team-count team-ids "round_robin"))
  ([team-ids pelada-format]
   (let [team-count (count team-ids)
         fmt (or pelada-format "round_robin")]
     (cond
       (< team-count 2)
       (throw (ex-info "At least two teams are required" {:type :bad-request
                                                          :message "At least two teams are required"}))

       (and (= fmt "championship_final") (< team-count 3))
       (throw (ex-info "At least three teams are required for Group Stage + Final"
                       {:type :bad-request
                        :message "At least three teams are required for Group Stage + Final"}))

       (and (= fmt "championship_semis_final") (< team-count 4))
       (throw (ex-info "At least four teams are required for Group Stage + Semifinals & Final"
                       {:type :bad-request
                        :message "At least four teams are required for Group Stage + Semifinals & Final"}))

       (and (= fmt "winner_stays_on") (< team-count 3))
       (throw (ex-info "At least three teams are required for Winner Stays On"
                       {:type :bad-request
                        :message "At least three teams are required for Winner Stays On"}))

       :else (vec team-ids)))))

(s/defn get-voting-info :- s/Any
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db) ;; Re-fetch pelada to ensure latest status and closed_at
        can-vote (try (logic.vote/validate-voting-eligibility pelada) true (catch Exception _ false))
        has-voted (db.vote/has-voter-voted? pelada-id player-id db)]
    {:can-vote can-vote
     :has-voted has-voted
     :eligible-players [] ;; In this simplified context we don't need players
     :message (if (not can-vote)
                "Voting is not open or has closed."
                "")}))

(defn ensure-startable
  "Validate pelada start preconditions. Returns vector of team ids."
  [pelada team-ids]
  (ensure-open pelada)
  (ensure-schedulable-team-count team-ids (:format pelada)))

(defn default-stage-for-format
  "Returns the initial match stage string for a given pelada format."
  [pelada-format]
  (if (contains? #{"championship_final" "championship_semis_final"} pelada-format)
    "group"
    "regular"))

(defn schedule-matches-for-start
  "Return sequence of match maps {:home :away :stage} honoring optional matches-per-team and format."
  ([team-ids matches-per-team]
   (schedule-matches-for-start team-ids matches-per-team "round_robin"))
  ([team-ids matches-per-team pelada-format]
   (let [ids (vec team-ids)
         fmt (or pelada-format "round_robin")
         stage (default-stage-for-format fmt)]
     (if (= fmt "winner_stays_on")
       (if (>= (count ids) 2)
         [{:home (nth ids 0) :away (nth ids 1) :stage "regular"}]
         [])
       (let [raw (if matches-per-team
                   (schedule/schedule-matches-with-limit ids matches-per-team)
                   (schedule/schedule-matches ids))]
         (mapv #(assoc % :stage stage) raw))))))

(defn match-plan->rows
  "Convert scheduled matches into DB ready rows."
  [pelada-id scheduled-matches]
  (map-indexed (fn [index {:keys [home away stage]}]
                 {:pelada-id pelada-id
                  :home-team-id home
                  :away-team-id away
                  :sequence (inc index)
                  :status "scheduled"
                  :home-score 0
                  :away-score 0
                  :stage (or stage "regular")})
               scheduled-matches))

(def resolve-knockout-winner-and-loser
  pelada-summary/resolve-match-winner-and-loser)

(defn- advance-staying-team
  [staying-id leaving-id clean-queue streak max-wins]
  (let [wins (inc (get streak staying-id 0))]
    (if (and (pos? max-wins) (>= wins max-wins) (>= (count clean-queue) 2))
      {:next-home (nth clean-queue 0)
       :next-away (nth clean-queue 1)
       :queue (-> (subvec clean-queue 2) (conj leaving-id) (conj staying-id))
       :streak {}}
      {:next-home staying-id
       :next-away (first clean-queue)
       :queue (-> (vec (rest clean-queue)) (conj leaving-id))
       :streak {staying-id wins}})))

(defn compute-winner-stays-on-next-match
  "Replays finished matches in sequence order for `winner_stays_on` and returns
   the next match map `{:home <team-id> :away <team-id> :stage \"regular\"}`."
  [team-ids finished-matches format-config]
  (let [ids (vec team-ids)
        draw-rule (or (:draw_rule format-config) (:draw-rule format-config) "challenger_stays")
        max-wins (int (or (:max_consecutive_wins format-config) (:max-consecutive-wins format-config) 0))]
    (if (< (count ids) 2)
      nil
      (let [ordered-matches (sort-by :sequence finished-matches)]
        (if (empty? ordered-matches)
          {:home (nth ids 0) :away (nth ids 1) :stage "regular"}
          (let [initial-queue (vec (drop 2 ids))
                final-state
                (reduce
                 (fn [{:keys [queue streak]} m]
                   (let [home-id (or (:home-team-id m) (:home_team_id m))
                         away-id (or (:away-team-id m) (:away_team_id m))
                         hs (int (or (:home-score m) (:home_score m) 0))
                         as (int (or (:away-score m) (:away_score m) 0))
                         clean-queue (->> (concat queue ids)
                                          distinct
                                          (remove #{home-id away-id})
                                          vec)]
                     (cond
                       (> hs as)
                       (advance-staying-team home-id away-id clean-queue streak max-wins)

                       (< hs as)
                       (advance-staying-team away-id home-id clean-queue streak max-wins)

                       :else
                       (case draw-rule
                         "both_leave"
                         (if (>= (count clean-queue) 2)
                           {:next-home (nth clean-queue 0)
                            :next-away (nth clean-queue 1)
                            :queue (-> (subvec clean-queue 2) (conj home-id) (conj away-id))
                            :streak {}}
                           {:next-home (first clean-queue)
                            :next-away away-id
                            :queue [home-id]
                            :streak {}})

                         "defender_stays"
                         (advance-staying-team home-id away-id clean-queue streak max-wins)

                         ;; Default: "challenger_stays" (Away stays, Home/oldest leaves)
                         (advance-staying-team away-id home-id clean-queue streak max-wins)))))
                 {:queue initial-queue :streak {}}
                 ordered-matches)]
            (when (and (:next-home final-state) (:next-away final-state))
              {:home (:next-home final-state)
               :away (:next-away final-state)
               :stage "regular"})))))))

(defn compute-next-progression-matches
  "Pure state-machine calculation returning a vector of new match specs
   `[{:home <uuid> :away <uuid> :stage <str>}]` to create when a match finishes."
  [pelada all-matches teams]
  (let [pelada-id (:id pelada)
        fmt (or (:format pelada) "round_robin")
        fmt-config (or (:format-config pelada) {})
        group-matches (filter #(contains? #{"regular" "group"} (or (:stage %) "regular")) all-matches)
        all-group-finished? (and (seq group-matches)
                                 (every? #(= "finished" (:status %)) group-matches))
        teams-with-pelada (map #(assoc % :pelada-id pelada-id) teams)]
    (case fmt
      "championship_final"
      (let [final-exists? (some #(= "final" (:stage %)) all-matches)]
        (if (and all-group-finished? (not final-exists?) (>= (count teams) 2))
          (let [standings (get (pelada-summary/team-standings group-matches teams-with-pelada) pelada-id)
                first-team (:team_id (nth standings 0 nil))
                second-team (:team_id (nth standings 1 nil))]
            (if (and first-team second-team (not= first-team second-team))
              [{:home first-team :away second-team :stage "final"}]
              []))
          []))

      "championship_semis_final"
      (let [sf1 (first (filter #(= "semifinal_1" (:stage %)) all-matches))
            sf2 (first (filter #(= "semifinal_2" (:stage %)) all-matches))
            final-exists? (some #(= "final" (:stage %)) all-matches)]
        (cond
          (and all-group-finished? (nil? sf1) (nil? sf2) (>= (count teams) 4))
          (let [standings (get (pelada-summary/team-standings group-matches teams-with-pelada) pelada-id)
                t1 (:team_id (nth standings 0 nil))
                t2 (:team_id (nth standings 1 nil))
                t3 (:team_id (nth standings 2 nil))
                t4 (:team_id (nth standings 3 nil))]
            (if (and t1 t2 t3 t4)
              [{:home t1 :away t4 :stage "semifinal_1"}
               {:home t2 :away t3 :stage "semifinal_2"}]
              []))

          (and sf1 sf2
               (= "finished" (:status sf1))
               (= "finished" (:status sf2))
               (not final-exists?))
          (let [[w1 l1] (resolve-knockout-winner-and-loser sf1)
                [w2 l2] (resolve-knockout-winner-and-loser sf2)
                include-3rd? (boolean (or (:include_third_place fmt-config) (:include-third-place fmt-config)))]
            (cond-> []
              (and include-3rd? l1 l2 (not= l1 l2))
              (conj {:home l1 :away l2 :stage "third_place"})
              (and w1 w2 (not= w1 w2))
              (conj {:home w1 :away w2 :stage "final"})))

          :else []))

      "winner_stays_on"
      (let [has-unfinished? (some #(not= "finished" (:status %)) all-matches)
            finished-matches (filter #(= "finished" (:status %)) all-matches)]
        (if (and (not has-unfinished?) (seq finished-matches) (>= (count teams) 2))
          (if-let [next-spec (compute-winner-stays-on-next-match
                              (mapv :id teams)
                              finished-matches
                              fmt-config)]
            [next-spec]
            [])
          []))

      [])))
