(ns api-peladaapp.controllers.match
  (:require
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.match-event :as db.match-event]
   [api-peladaapp.db.match-lineup :as db.match-lineup]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.match :as match.logic]
   [api-peladaapp.logic.match-event :as match-event.logic]
   [api-peladaapp.logic.pelada :as pelada.logic]
   [api-peladaapp.logic.support-lineup :as support-lineup]
   [api-peladaapp.models.match :as models.match]
   [api-peladaapp.models.match-event :as models.match-event]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn list-matches :- [models.match/Match]
  [pelada-id :- s/Uuid db]
  (db.match/list-matches-by-pelada pelada-id db))

(s/defn get-match :- (s/maybe models.match/Match)
  [match-id :- s/Uuid db]
  (db.match/get-match match-id db))

(defn- create-dynamic-matches!
  [pelada new-match-specs existing-matches teams tx]
  (when (seq new-match-specs)
    (let [pelada-id (:id pelada)
          max-seq (reduce max 0 (map :sequence existing-matches))
          team-ids (mapv :id teams)
          team-players (db.team/list-team-players-by-pelada pelada-id tx)
          confirmed-players (vec (db.attendance/list-confirmed-players-by-pelada pelada-id tx))
          confirmed-player-ids (mapv :player-id confirmed-players)
          match-lineups (when (= "winner_stays_on" (:format pelada))
                          (db.match-lineup/list-match-lineups-by-pelada pelada-id tx))]
      (loop [[spec & more] new-match-specs
             curr-seq (inc max-seq)
             accumulated-matches (vec existing-matches)]
        (when spec
          (let [draft-match {:pelada-id pelada-id
                             :home-team-id (:home spec)
                             :away-team-id (:away spec)
                             :sequence curr-seq
                             :status "scheduled"
                             :home-score 0
                             :away-score 0
                             :stage (or (:stage spec) "regular")}
                with-support (support-lineup/reroll-single-match draft-match
                                                                 accumulated-matches
                                                                 team-ids
                                                                 team-players
                                                                 confirmed-player-ids)
                match-id (db.match/insert-match with-support tx)
                custom-lineups (when (= "winner_stays_on" (:format pelada))
                                 (pelada.logic/compute-winner-stays-on-lineups
                                  pelada
                                  spec
                                  (filter #(= "finished" (:status %)) accumulated-matches)
                                  match-lineups
                                  team-players
                                  confirmed-players))
                seeded (if custom-lineups
                         (db.match-lineup/seed-custom-lineup!
                          match-id
                          (:home spec)
                          (:away spec)
                          (:home-lineup custom-lineups)
                          (:away-lineup custom-lineups)
                          pelada
                          tx)
                         0)]
            (when (zero? seeded)
              (db.match-lineup/ensure-seeded match-id tx))
            (recur more (inc curr-seq) (conj accumulated-matches (assoc with-support :id match-id)))))))))

(defn- maybe-advance-pelada-progression!
  [pelada-id tx]
  (let [pelada (db.pelada/get-pelada pelada-id tx)
        fmt (or (:format pelada) "round_robin")]
    (when (not= fmt "round_robin")
      (let [all-matches (db.match/list-matches-by-pelada pelada-id tx)
            teams (db.team/list-pelada-teams pelada-id tx)
            new-specs (pelada.logic/compute-next-progression-matches pelada all-matches teams)]
        (create-dynamic-matches! pelada new-specs all-matches teams tx)))))

(s/defn update-score :- models.match/Match
  [match-id :- s/Uuid score-update db]
  (let [validated-update (match.logic/build-score-update score-update)]
    (jdbc/with-transaction [tx db]
      (let [old-match (db.match/get-match match-id tx)
            teams-changed? (or (and (:home-team-id validated-update)
                                    (not= (:home-team-id validated-update) (:home-team-id old-match)))
                               (and (:away-team-id validated-update)
                                    (not= (:away-team-id validated-update) (:away-team-id old-match))))]
        (db.match/update-score match-id validated-update tx)
        (when teams-changed?
          (db.match-lineup/delete-by-match match-id tx)
          (db.match-lineup/ensure-seeded match-id tx))
        (when (and (= "finished" (:status validated-update))
                   (not= "finished" (:status old-match)))
          (maybe-advance-pelada-progression! (:pelada-id old-match) tx))
        (db.match/get-match match-id tx)))))

(s/defn start-match-timer :- models.match/Match
  [match-id :- s/Uuid db]
  (let [match (db.match/get-match match-id db)
        now (str (java.time.Instant/now))
        should-update-timer? (not= "running" (:timer-status match))
        should-update-status? (= "scheduled" (:status match))]
    (if (or should-update-timer? should-update-status?)
      (do
        (db.match/update-match match-id
                               (cond-> {}
                                 should-update-timer? (assoc :timer-status "running" :timer-started-at now)
                                 should-update-status? (assoc :status "running"))
                               db)
        (db.match/get-match match-id db))
      match)))

(s/defn pause-match-timer :- models.match/Match
  [match-id :- s/Uuid db]
  (let [match (db.match/get-match match-id db)]
    (if (not= "running" (:timer-status match))
      match
      (let [now (java.time.Instant/now)
            started-at (helpers.time/->instant (:timer-started-at match))
            elapsed (.toMillis (java.time.Duration/between started-at now))
            new-accumulated (+ (or (:timer-accumulated-ms match) 0) elapsed)]
        (db.match/update-match match-id {:timer-status "paused"
                                         :timer-started-at nil
                                         :timer-accumulated-ms new-accumulated} db)
        (db.match/get-match match-id db)))))

(s/defn reset-match-timer :- models.match/Match
  [match-id :- s/Uuid db]
  (db.match/update-match match-id {:timer-status "stopped"
                                   :timer-started-at nil
                                   :timer-accumulated-ms 0} db)
  (db.match/get-match match-id db))

(s/defn create-event :- models.match-event/MatchEvent
  [match-id :- s/Uuid {:keys [player-id event-type session-time-ms match-time-ms assistant-id team-id]} db]
  (let [player-id (match-event.logic/ensure-player-id player-id)
        canonical-type (match-event.logic/canonical-type event-type)]
    (jdbc/with-transaction [tx db]
      (let [event-id (db.match-event/insert-event match-id player-id canonical-type session-time-ms match-time-ms nil team-id tx)]
        (when (and (= canonical-type "goal") assistant-id)
          (db.match-event/insert-event match-id assistant-id "assist" session-time-ms match-time-ms event-id team-id tx))
        (db.match-event/get-event event-id tx)))))

(s/defn list-events-by-pelada :- [models.match-event/MatchEvent]
  [pelada-id :- s/Uuid db]
  (db.match-event/list-events-by-pelada pelada-id db))

(defn- find-associated-assist
  [match-id goal-event tx]
  (or (db.match-event/get-assist-by-goal-id (:id goal-event) tx)
      (db.match-event/get-assist-by-time match-id (:session-time-ms goal-event) (:match-time-ms goal-event) (:player-id goal-event) tx)))

(s/defn delete-last-event :- s/Int
  [match-id :- s/Uuid {:keys [player-id event-type id]} db]
  (let [player-id (match-event.logic/ensure-player-id player-id)
        canonical-type (match-event.logic/canonical-type event-type)]
    (jdbc/with-transaction [tx db]
      (if-let [event (if id
                       (db.match-event/get-event id tx)
                       (db.match-event/get-last-event match-id player-id canonical-type tx))]
        (do
          (when (= (:event-type event) "goal")
            (when-let [assist (find-associated-assist match-id event tx)]
              (db.match-event/delete-event-by-id (:id assist) tx)))
          (db.match-event/delete-event-by-id (:id event) tx))
        0))))

(s/defn update-event :- models.match-event/MatchEvent
  [match-id :- s/Uuid event-id :- s/Uuid {:keys [player-id assistant-id]} db]
  (jdbc/with-transaction [tx db]
    (let [goal-event (db.match-event/get-event event-id tx)]
      (if (and goal-event (or (= (:event-type goal-event) "goal") (= (:event-type goal-event) "own_goal")))
        (let [old-session (:session-time-ms goal-event)
              old-match (:match-time-ms goal-event)
              updated-goal (db.match-event/update-event-player event-id player-id tx)
              existing-assist (find-associated-assist match-id goal-event tx)]
          (when (= (:event-type goal-event) "goal")
            (cond
              assistant-id
              (if existing-assist
                (db.match-event/update-event-player (:id existing-assist) assistant-id tx)
                (db.match-event/insert-event match-id assistant-id "assist" old-session old-match event-id (:team-id updated-goal) tx))
              (not assistant-id)
              (when existing-assist
                (db.match-event/delete-event-by-id (:id existing-assist) tx))))
          updated-goal)
        (throw (Exception. "Event not found or is not a goal/own-goal"))))))

(s/defn list-player-stats-by-pelada :- [models.match/PlayerStats]
  [pelada-id :- s/Uuid db]
  (db.match-event/list-player-stats-by-pelada pelada-id db))

;; Match lineups (per-match players)
(s/defn list-lineups-by-match :- {s/Uuid [s/Any]}
  [match-id :- s/Uuid db]
  (do (db.match-lineup/ensure-seeded match-id db)
      (db.match-lineup/list-by-match-grouped match-id db)))

(s/defn add-lineup-player :- s/Int
  [match-id :- s/Uuid {:keys [team-id player-id]} db]
  (do (db.match-lineup/ensure-seeded match-id db)
      (db.match-lineup/add-player match-id team-id player-id db)))

(s/defn remove-lineup-player :- s/Int
  [match-id :- s/Uuid {:keys [team-id player-id]} db]
  (do (db.match-lineup/ensure-seeded match-id db)
      (db.match-lineup/remove-player match-id team-id player-id db)))

(s/defn replace-lineup-player :- s/Int
  [match-id :- s/Uuid {:keys [team-id out-player-id in-player-id]} db]
  (do (db.match-lineup/ensure-seeded match-id db)
      (db.match-lineup/replace-player match-id team-id out-player-id in-player-id db)))

(s/defn update-support-lineup :- models.match/Match
  [match-id :- s/Uuid update-data db]
  (db.match/update-match match-id update-data db)
  (db.match/get-match match-id db))

(s/defn reroll-support-lineup :- models.match/Match
  [match-id :- s/Uuid db]
  (let [target-match (db.match/get-match match-id db)
        pelada-id (:pelada-id target-match)
        all-matches (db.match/list-matches-by-pelada pelada-id db)
        other-matches (remove #(= (:id %) match-id) all-matches)
        teams (db.team/list-pelada-teams pelada-id db)
        team-ids (mapv :id teams)
        team-players (db.team/list-team-players-by-pelada pelada-id db)
        confirmed-player-ids (mapv :player-id (db.attendance/list-confirmed-players-by-pelada pelada-id db))
        updated-match (support-lineup/reroll-single-match target-match other-matches team-ids team-players confirmed-player-ids)]
    (db.match/update-match match-id
                           {:support-camera-player-id (:support-camera-player-id updated-match)
                            :support-stats-player-id (:support-stats-player-id updated-match)}
                           db)
    (db.match/get-match match-id db)))
