(ns api-peladaapp.controllers.pelada
  (:require [api-peladaapp.db.match :as db.match]
            [api-peladaapp.db.match-event :as db.match-event]
            [api-peladaapp.db.match-lineup :as db.match-lineup]
            [api-peladaapp.db.pelada :as db.pelada]
            [api-peladaapp.db.player :as db.player]
            [api-peladaapp.db.team :as db.team]
            [api-peladaapp.db.user :as db.user]
            [api-peladaapp.models.user :as models.user]
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

(s/defschema PeladaClosedResponse {:updated s/Int})

(s/defn close-pelada :- PeladaClosedResponse
  [pelada-id :- s/Int db]
  (db.match/finish-all-by-pelada pelada-id db)
  {:updated (db.pelada/update-pelada pelada-id {:status "closed" :closed_at (str (java.time.Instant/now))} db)})

(s/defschema TeamLineupSchema {(s/pred int? "int-key") [s/Any]})

(s/defschema PeladaDashboardResponse
  {:pelada s/Any
   :matches [s/Any]
   :teams [s/Any]
   :users [models.user/PublicUser]
   :organization-players [s/Any]
   :match-events [s/Any]
   :player-stats (s/maybe [s/Any])
   :team-players-map {s/Int [s/Any]}
   :match-lineups-map {s/Int TeamLineupSchema}})

(s/defn get-pelada-dashboard-data :- PeladaDashboardResponse
  [pelada-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        organization-id (:organization_id pelada)
        matches (db.match/list-matches-by-pelada pelada-id db)
        teams (db.team/list-pelada-teams pelada-id db)
        users (map #(dissoc % :password :email) (db.user/list-users db 0 1000000))
        organization-players (db.player/list-players-by-organization organization-id db)
        match-events (db.match-event/list-events-by-pelada pelada-id db)
        player-stats (try (db.match-event/list-player-stats-by-pelada pelada-id db) (catch Exception _ nil))
        team-players (db.team/list-team-players-by-pelada pelada-id db) ; New function
        match-lineups (db.match-lineup/list-match-lineups-by-pelada pelada-id db) ; New function

        ;; Transform team-players into a map
        team-players-map (group-by :team_id team-players)

        ;; Transform match-lineups into a map of match-id -> team-id -> players
        match-lineups-map (reduce (fn [acc {:keys [match_id team_id] :as lineup}]
                                    (assoc-in acc [match_id team_id] (conj (get-in acc [match_id team_id] []) lineup)))
                                  {}
                                  match-lineups)]
    {:pelada pelada
     :matches matches
     :teams teams
     :users users
     :organization_players organization-players
     :match_events match-events
     :player_stats player-stats
     :team_players_map team-players-map
     :match_lineups_map match-lineups-map}))
