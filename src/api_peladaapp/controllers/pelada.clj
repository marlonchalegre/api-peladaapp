(ns api-peladaapp.controllers.pelada
  (:require [api-peladaapp.db.match :as db.match]
            [api-peladaapp.db.match-lineup :as db.match-lineup]
            [api-peladaapp.db.pelada :as db.pelada]
            [api-peladaapp.db.team :as db.team]
            [api-peladaapp.logic.pelada :as pelada.logic]
            [schema.core :as s]))

(defn- auto-create-teams!
  [pelada-id team-count db]
  (when (pos? team-count)
    (->> (range 1 (inc team-count))
         (map (fn [index]
                {:pelada_id pelada-id
                 :name (str "Time " index)}))
         (run! #(db.team/insert-team % db)))))

(defn- fetch-team-ids
  [pelada-id db]
  (->> (db.team/list-pelada-teams pelada-id db)
       (map :id)
       vec))

(defn- persist-match-plan!
  [pelada-id match-plan db]
  (->> (pelada.logic/match-plan->rows pelada-id match-plan)
       (run! #(db.match/insert-match % db))))

(defn- seed-lineups-from-teams!
  [pelada-id db]
  (->> (db.match/list-matches-by-pelada pelada-id db)
       (map :id)
       (run! #(db.match-lineup/ensure-seeded % db))))

(s/defn create-pelada :- s/Int
  "Create pelada and optionally seed default teams. Returns pelada id."
  [pelada db]
  (let [pelada-id (db.pelada/insert-pelada pelada db)]
    (when-let [team-count (:num_teams pelada)]
      (auto-create-teams! pelada-id team-count db))
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
  "Generate matches for a pelada, transition it to running, and seed lineups."
  [pelada-id :- s/Int db & [opts]]
  (let [matches-per-team (:matches_per_team (or opts {}))
        pelada (db.pelada/get-pelada pelada-id db)
        team-ids (->> (fetch-team-ids pelada-id db)
                      (pelada.logic/ensure-startable pelada))
        match-plan (pelada.logic/schedule-matches-for-start team-ids matches-per-team)]
    (persist-match-plan! pelada-id match-plan db)
    (db.pelada/update-pelada pelada-id {:status "running"} db)
    (seed-lineups-from-teams! pelada-id db)
    {:matches-created (count match-plan)}))

(s/defn close-pelada :- s/Int
  [pelada-id :- s/Int db]
  (db.match/finish-all-by-pelada pelada-id db)
  (db.pelada/update-pelada pelada-id {:status "closed" :closed_at (java.time.Instant/now)} db))
