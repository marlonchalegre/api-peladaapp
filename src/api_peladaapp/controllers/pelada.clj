(ns api-peladaapp.controllers.pelada
  (:require
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.match-event :as db.match-event]
   [api-peladaapp.db.match-lineup :as db.match-lineup]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.logic.pelada :as pelada.logic]
   [api-peladaapp.models.pelada :as models.pelada]
   [api-peladaapp.responses.pelada :as responses.pelada]
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

(s/defn create-pelada :- models.pelada/Pelada
  "Create pelada and optionally seed default teams. Returns pelada model."
  [pelada :- models.pelada/Pelada
   db]
  (let [pelada-id (db.pelada/insert-pelada pelada db)]
    (when-let [team-count (:num-teams pelada)]
      (auto-create-teams! pelada-id team-count db))
    (db.pelada/get-pelada pelada-id db)))

(s/defn get-pelada :- models.pelada/Pelada
  [pelada-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (if (nil? pelada)
      (throw (ex-info nil {:type :not-found :message "Pelada not found"}))
      pelada)))

(s/defn update-pelada :- models.pelada/Pelada
  [pelada-id :- s/Int pelada :- models.pelada/Pelada db]
  (let [rows (db.pelada/update-pelada pelada-id pelada db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Pelada not found"}))
      (db.pelada/get-pelada pelada-id db))))

(s/defn delete-pelada :- s/Int
  [pelada-id :- s/Int db]
  (let [rows (db.pelada/delete-pelada pelada-id db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Pelada not found"}))
      rows)))

(s/defn list-peladas
  [organization-id :- s/Int db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        peladas (db.pelada/list-peladas organization-id per-page offset db)
        total-count (db.pelada/count-peladas organization-id db)]
    (pagination/with-pagination-headers peladas total-count page per-page)))

(s/defn begin-pelada :- responses.pelada/PeladaBeginResponse
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
    {:matches_created (count match-plan)}))

(s/defn close-pelada :- models.pelada/Pelada
  [pelada-id :- s/Int db]
  (db.match/finish-all-by-pelada pelada-id db)
  (db.pelada/update-pelada pelada-id {:status "closed" :closed-at (str (java.time.Instant/now))} db)
  (db.pelada/get-pelada pelada-id db))

(s/defn get-pelada-dashboard-data :- responses.pelada/PeladaDashboardResponse
  [pelada-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        organization-id (:organization-id pelada)
        matches (db.match/list-matches-by-pelada pelada-id db)
        teams (db.team/list-pelada-teams pelada-id db)
        users (map #(dissoc % :password :email) (db.user/list-users db 0 1000000))
        organization-players (db.player/list-players-by-organization organization-id db)
        match-events (db.match-event/list-events-by-pelada pelada-id db)
        player-stats (try (db.match-event/list-player-stats-by-pelada pelada-id db) (catch Exception _ nil))
        team-players (db.team/list-team-players-by-pelada pelada-id db)
        match-lineups (db.match-lineup/list-match-lineups-by-pelada pelada-id db)

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

(s/defn get-pelada-full-details-controller :- responses.pelada/PeladaFullDetailsResponse
  [pelada-id :- s/Int
   user-id :- s/Int
   db]
  (let [pelada-data (db.pelada/get-pelada-full-details pelada-id db)
        pelada (:pelada pelada-data)
        all-org-players (:org_players_map pelada-data)
        current-player (some-> (filter #(= user-id (:user_id %)) (vals all-org-players)) first)
        player-id (:id current-player)
        voting-info (if (and (= "closed" (:status pelada)) player-id)
                      (pelada.logic/get-voting-info pelada-id player-id db)
                      nil)]
    (assoc pelada-data :voting_info voting-info)))
