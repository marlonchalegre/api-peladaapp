(ns api-100folego.controllers.pelada
  (:require [api-100folego.db.match :as db.match]
            [api-100folego.db.pelada :as db.pelada]
            [api-100folego.db.team :as db.team]
            [api-100folego.db.match-lineup :as db.match-lineup]
            [api-100folego.logic.schedule :as schedule]
            [schema.core :as s]))

(s/defn create-pelada :- s/Int
  "Create pelada and, if :num_teams is provided, auto-create teams named
  'Time 1'..'Time N' bound to the pelada. Returns pelada id."
  [pelada db]
  (let [pelada-id (db.pelada/insert-pelada pelada db)
        n (:num_teams pelada)]
    (when (and n (pos? n))
      (doseq [i (range 1 (inc n))]
        (db.team/insert-team {:pelada_id pelada-id
                              :name (str "Time " i)} db)))
    pelada-id))

(s/defn get-pelada :- s/Any
  [pelada-id :- s/Int db]
  (db.pelada/get-pelada pelada-id db))

(s/defn update-pelada :- s/Int
  [pelada-id :- s/Int pelada db]
  (db.pelada/update-pelada pelada-id pelada db))

(s/defn delete-pelada :- s/Int
  [pelada-id :- s/Int db]
  (db.pelada/delete-pelada pelada-id db))

(s/defn list-peladas :- [s/Any]
  [organization-id :- s/Int db]
  (db.pelada/list-peladas organization-id db))

(s/defn begin-pelada :- {:matches-created s/Int}
  "Generate matches based on teams in pelada and update status to running. If
  :matches_per_team is provided, schedule that many per team under constraints."
  [pelada-id :- s/Int db & [opts]]
  (let [matches_per_team (:matches_per_team (or opts {}))
        pelada (db.pelada/get-pelada pelada-id db)
        _ (when (not= "open" (:status pelada))
            (throw (ex-info nil {:type :bad-request :message "Pelada already started or closed"})))
        teams (db.team/list-pelada-teams pelada-id db)
        team-ids (map :id teams)
        _ (when (< (count team-ids) 2)
            (throw (ex-info nil {:type :bad-request :message "At least two teams are required"})))
        _ (when (odd? (count team-ids))
            (throw (ex-info nil {:type :bad-request :message "Number of teams must be even"})))
        pairs (if matches_per_team
                (schedule/schedule-matches-with-limit team-ids matches_per_team)
                (schedule/schedule-matches team-ids))
        _ (doseq [[idx {:keys [home away]}] (map-indexed vector pairs)]
            (db.match/insert-match {:pelada_id pelada-id
                                    :home_team_id home
                                    :away_team_id away
                                    :sequence (inc idx)
                                    :status "scheduled"
                                    :home_score 0
                                    :away_score 0}
                                   db))
        _ (db.pelada/update-pelada pelada-id {:status "running"} db)
        ;; Seed per-match lineups from current team players
        matches (db.match/list-matches-by-pelada pelada-id db)
        _ (doseq [m matches]
            (db.match-lineup/ensure-seeded (:id m) db))]
    {:matches-created (count pairs)}))

(s/defn close-pelada :- s/Int
  [pelada-id :- s/Int db]
  (db.match/finish-all-by-pelada pelada-id db)
  (db.pelada/update-pelada pelada-id {:status "closed"} db))
