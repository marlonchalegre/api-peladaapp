(ns api-peladaapp.db.match
  (:require
   [api-peladaapp.adapters.match :as adapter.match]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-match :- s/Int
  [{:keys [pelada-id home-team-id away-team-id sequence status home-score away-score]}
   db]
  (-> (sql/insert! db :matches {:pelada_id pelada-id
                                :home_team_id home-team-id
                                :away_team_id away-team-id
                                :sequence sequence
                                :status status
                                :home_score home-score
                                :away_score away-score})
      affected-rows-count))

(s/defn list-matches-by-pelada :- [s/Any]
  [pelada-id db]
  (->> (sql/find-by-keys db :matches {:pelada_id pelada-id})
       (sort-by :matches/sequence)
       (map adapter.match/db->model)))

(s/defn get-match [id db]
  (-> (sql/get-by-id db :matches id)
      adapter.match/db->model))

(s/defn update-match :- s/Int
  [id {:keys [home-score away-score status timer-started-at timer-accumulated-ms timer-status]} db]
  (-> (sql/update! db :matches (cond-> {}
                                 (some? home-score) (assoc :home_score home-score)
                                 (some? away-score) (assoc :away_score away-score)
                                 status (assoc :status status)
                                 timer-started-at (assoc :timer_started_at timer-started-at)
                                 (some? timer-accumulated-ms) (assoc :timer_accumulated_ms timer-accumulated-ms)
                                 timer-status (assoc :timer_status timer-status))
                   {:id id})
      affected-rows-count))

(s/defn update-score :- s/Int
  [id data db]
  (update-match id data db))

(s/defn update-sequence :- s/Int
  [id sequence db]
  (-> (sql/update! db :matches {:sequence sequence} {:id id})
      affected-rows-count))

(s/defn finish-all-by-pelada
  "Finish all matches for a pelada. Matches not yet finished are closed as 0x0 draws."
  [pelada-id :- s/Int db]
  (jdbc/execute! db ["UPDATE matches SET status = 'finished', home_score = 0, away_score = 0 WHERE pelada_id = ? AND status != 'finished'" pelada-id]))
