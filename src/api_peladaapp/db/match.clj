(ns api-peladaapp.db.match
  (:require
   [api-peladaapp.adapters.match :as adapter.match]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.helpers.time :as helpers.time]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn insert-match :- s/Uuid
  [{:keys [pelada-id home-team-id away-team-id sequence status home-score away-score]} :- {:pelada-id s/Uuid
                                                                                           :home-team-id s/Uuid
                                                                                           :away-team-id s/Uuid
                                                                                           :sequence s/Int
                                                                                           :status s/Str
                                                                                           (s/optional-key :home-score) (s/maybe s/Int)
                                                                                           (s/optional-key :away-score) (s/maybe s/Int)}
   db]
  (let [row {:pelada_id pelada-id
             :home_team_id home-team-id
             :away_team_id away-team-id
             :sequence sequence
             :status [:cast status :match_status]
             :home_score home-score
             :away_score away-score}
        query (-> (h/insert-into :Matches)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))

(s/defn list-matches-by-pelada :- [s/Any]
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :Matches)
                  (h/where [:= :pelada_id pelada-id])
                  (h/order-by :sequence))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map adapter.match/db->model))))

(s/defn get-match [id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :Matches)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        adapter.match/db->model)))

(s/defn update-match :- s/Int
  [id :- s/Uuid {:keys [home-score away-score status timer-started-at timer-accumulated-ms timer-status]} db]
  (let [db-row (cond-> {}
                 (some? home-score) (assoc :home_score home-score)
                 (some? away-score) (assoc :away_score away-score)
                 status (assoc :status [:cast status :match_status])
                 timer-started-at (assoc :timer_started_at [[:cast (helpers.time/to-utc-timestamp-str timer-started-at) :timestamp]])
                 (some? timer-accumulated-ms) (assoc :timer_accumulated_ms timer-accumulated-ms)
                 timer-status (assoc :timer_status [:cast timer-status :timer_status]))]
    (if (empty? db-row)
      1
      (let [query (-> (h/update :Matches)
                      (h/set db-row)
                      (h/where [:= :id id]))]
        (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
            hsql/affected-rows-count)))))

(s/defn update-score :- s/Int
  [id :- s/Uuid data db]
  (update-match id data db))

(s/defn update-sequence :- s/Int
  [id :- s/Uuid sequence :- s/Int db]
  (let [query (-> (h/update :Matches)
                  (h/set {:sequence sequence})
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn finish-all-by-pelada
  "Finish all matches for a pelada. Matches not yet finished are closed.
   It also ensures timers are stopped and accumulated time is calculated for running matches."
  [pelada-id :- s/Uuid db]
  (let [;; Note: Postgres handles interval arithmetic better with native types.
        ;; Using :raw for EXTRACT to ensure "FROM" keyword is used instead of comma.
        query (-> (h/update :Matches)
                  (h/set {:timer_accumulated_ms
                          [:case
                           [:= :timer_status [:cast "running" :timer_status]]
                           [:+ [:coalesce :timer_accumulated_ms 0]
                            [:* [:raw "EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - \"timer_started_at\"))"] 1000]]
                           :else [:coalesce :timer_accumulated_ms 0]]
                          :timer_status [:cast "paused" :timer_status]
                          :timer_started_at nil
                          :status [:cast "finished" :match_status]
                          :home_score [:coalesce :home_score 0]
                          :away_score [:coalesce :away_score 0]})
                  (h/where [:= :pelada_id pelada-id] [:!= :status [:cast "finished" :match_status]]))]
    (jdbc/execute! db (hsql/format query))))
