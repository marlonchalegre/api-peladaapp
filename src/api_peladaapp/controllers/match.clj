(ns api-peladaapp.controllers.match
  (:require
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.match-event :as db.match-event]
   [api-peladaapp.db.match-lineup :as db.match-lineup]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.match :as match.logic]
   [api-peladaapp.logic.match-event :as match-event.logic]
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

(s/defn update-score :- models.match/Match
  [match-id :- s/Uuid score-update db]
  (let [validated-update (match.logic/build-score-update score-update)]
    (db.match/update-score match-id validated-update db)
    (db.match/get-match match-id db)))

(s/defn start-match-timer :- models.match/Match
  [match-id :- s/Uuid db]
  (let [match (db.match/get-match match-id db)]
    (if (= "running" (:timer-status match))
      match
      (let [now (str (java.time.Instant/now))]
        (db.match/update-match match-id {:timer-status "running" :timer-started-at now} db)
        (db.match/get-match match-id db)))))

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
  [match-id :- s/Uuid {:keys [player-id event-type session-time-ms match-time-ms assistant-id]} db]
  (let [player-id (match-event.logic/ensure-player-id player-id)
        canonical-type (match-event.logic/canonical-type event-type)]
    (jdbc/with-transaction [tx db]
      (let [event-id (db.match-event/insert-event match-id player-id canonical-type session-time-ms match-time-ms tx)]
        (when (and (= canonical-type "goal") assistant-id)
          (db.match-event/insert-event match-id assistant-id "assist" session-time-ms match-time-ms tx))
        (db.match-event/get-event event-id tx)))))

(s/defn list-events-by-pelada :- [models.match-event/MatchEvent]
  [pelada-id :- s/Uuid db]
  (db.match-event/list-events-by-pelada pelada-id db))

(s/defn delete-last-event :- s/Int
  [match-id :- s/Uuid {:keys [player-id event-type id]} db]
  (let [player-id (match-event.logic/ensure-player-id player-id)
        canonical-type (match-event.logic/canonical-type event-type)]
    (jdbc/with-transaction [tx db]
      (if id
        (if-let [event (db.match-event/get-event id tx)]
          (do
            (when (= (:event-type event) "goal")
              (when-let [assist (db.match-event/get-assist-by-time match-id (:session-time-ms event) (:match-time-ms event) tx)]
                (db.match-event/delete-event-by-id (:id assist) tx)))
            (db.match-event/delete-event-by-id id tx))
          0)
        (if-let [event (db.match-event/get-last-event match-id player-id canonical-type tx)]
          (do
            (when (= (:event-type event) "goal")
              (when-let [assist (db.match-event/get-assist-by-time match-id (:session-time-ms event) (:match-time-ms event) tx)]
                (db.match-event/delete-event-by-id (:id assist) tx)))
            (db.match-event/delete-event-by-id (:id event) tx))
          0)))))

(s/defn update-event :- models.match-event/MatchEvent
  [match-id :- s/Uuid event-id :- s/Uuid {:keys [player-id assistant-id]} db]
  (jdbc/with-transaction [tx db]
    (let [goal-event (db.match-event/get-event event-id tx)]
      (if (and goal-event (or (= (:event-type goal-event) "goal") (= (:event-type goal-event) "own_goal")))
        (let [old-session (:session-time-ms goal-event)
              old-match (:match-time-ms goal-event)
              updated-goal (db.match-event/update-event-player event-id player-id tx)
              existing-assist (db.match-event/get-assist-by-time match-id old-session old-match tx)]
          (when (= (:event-type goal-event) "goal")
            (cond
              assistant-id
              (if existing-assist
                (db.match-event/update-event-player (:id existing-assist) assistant-id tx)
                (db.match-event/insert-event match-id assistant-id "assist" old-session old-match tx))
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
