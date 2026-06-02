(ns api-peladaapp.db.match-event
  (:require
   [api-peladaapp.adapters.match :as adapter.match]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.models.match :as models.match]
   [api-peladaapp.models.match-event :as models.match-event]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(defn- unqualify-row [row]
  (into {}
        (map (fn [[k v]]
               (let [kw (if (keyword? k) (keyword (name k)) k)]
                 [kw v])))
        row))

(s/defn get-event :- (s/maybe models.match-event/MatchEvent)
  [id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :MatchEvents)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        adapter.match/db-event->model)))

(s/defn insert-event :- s/Uuid
  ([match-id player-id event-type db]
   (insert-event match-id player-id event-type nil nil nil db))
  ([match-id player-id event-type session-time-ms match-time-ms db]
   (insert-event match-id player-id event-type session-time-ms match-time-ms nil db))
  ([match-id :- s/Uuid player-id :- s/Uuid event-type :- s/Str session-time-ms match-time-ms parent-event-id db]
   (let [row (cond-> {:match_id match-id
                      :player_id player-id
                      :event_type [:cast event-type :match_event_type]}
               session-time-ms (assoc :session_time_ms session-time-ms)
               match-time-ms (assoc :match_time_ms match-time-ms)
               parent-event-id (assoc :parent_event_id parent-event-id))
         query (-> (h/insert-into :MatchEvents)
                   (h/values [row])
                   (h/returning :id))]
     (:id (jdbc/execute-one! db (hsql/format query) hsql/opts)))))

(s/defn list-events-by-pelada :- [models.match-event/MatchEvent]
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :e.id :e.match_id :e.player_id :e.event_type :e.created_at :e.session_time_ms :e.match_time_ms :e.parent_event_id)
                  (h/from [:MatchEvents :e])
                  (h/join [:Matches :m] [:= :m.id :e.match_id])
                  (h/where [:= :m.pelada_id pelada-id])
                  (h/order-by :e.id))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map adapter.match/db-event->model))))

(s/defn delete-last-event :- s/Int
  [match-id :- s/Uuid player-id :- s/Uuid event-type :- s/Str db]
  (let [sub-query (-> (h/select :id)
                      (h/from :MatchEvents)
                      (h/where [:= :match_id match-id]
                               [:= :player_id player-id]
                               [:= :event_type [:cast event-type :match_event_type]])
                      (h/order-by [:id :desc])
                      (h/limit 1))
        query (-> (h/delete-from :MatchEvents)
                  (h/where [:in :id sub-query]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        (as-> res (if (map? res) 1 0)))))

(s/defn get-last-event :- (s/maybe models.match-event/MatchEvent)
  [match-id :- s/Uuid player-id :- s/Uuid event-type :- s/Str db]
  (let [query (-> (h/select :*)
                  (h/from :MatchEvents)
                  (h/where [:= :match_id match-id]
                           [:= :player_id player-id]
                           [:= :event_type [:cast event-type :match_event_type]])
                  (h/order-by [:id :desc])
                  (h/limit 1))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        adapter.match/db-event->model)))

(s/defn get-assist-by-time :- (s/maybe models.match-event/MatchEvent)
  [match-id :- s/Uuid session-time-ms :- (s/maybe s/Int) match-time-ms :- (s/maybe s/Int) scorer-player-id :- s/Uuid db]
  (let [query (-> (h/select :me.*)
                  (h/from [:MatchEvents :me])
                  (h/left-join [:MatchLineups :ml] [:and [:= :ml.match_id :me.match_id] [:= :ml.player_id :me.player_id]])
                  (h/left-join [:TeamPlayers :tp] [:= :tp.player_id :me.player_id])
                  (h/where [:= :me.match_id match-id]
                           [:= :me.event_type [:cast "assist" :match_event_type]]
                           [:= :me.session_time_ms session-time-ms]
                           [:= :me.match_time_ms match-time-ms]
                           [:= [:raw "COALESCE(ml.team_id, tp.team_id)"]
                            (-> (h/select [[:coalesce :ml2.team_id :tp2.team_id] :team_id])
                                (h/from [:Matches :m2])
                                (h/left-join [:MatchLineups :ml2] [:and [:= :ml2.match_id :m2.id] [:= :ml2.player_id scorer-player-id]])
                                (h/left-join [:Teams :t2] [:= :t2.pelada_id :m2.pelada_id])
                                (h/left-join [:TeamPlayers :tp2] [:and [:= :tp2.team_id :t2.id] [:= :tp2.player_id scorer-player-id]])
                                (h/where [:= :m2.id match-id])
                                (h/limit 1))])
                  (h/limit 1))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        adapter.match/db-event->model)))

(s/defn get-assist-by-goal-id :- (s/maybe models.match-event/MatchEvent)
  [goal-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :MatchEvents)
                  (h/where [:= :parent_event_id goal-id]
                           [:= :event_type [:cast "assist" :match_event_type]])
                  (h/limit 1))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        adapter.match/db-event->model)))

(s/defn delete-event-by-id :- s/Int
  [id :- s/Uuid db]
  (let [query (-> (h/delete-from :MatchEvents)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        (as-> res (if (map? res) 1 0)))))

(s/defn update-event-player :- (s/maybe models.match-event/MatchEvent)
  [id :- s/Uuid player-id :- s/Uuid db]
  (let [query (-> (h/update :MatchEvents)
                  (h/set {:player_id player-id})
                  (h/where [:= :id id]))]
    (jdbc/execute-one! db (hsql/format query) hsql/opts)
    (get-event id db)))

(s/defn list-player-stats-by-pelada :- [models.match/PlayerStats]
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :s.player_id :p.user_id :u.name :u.avatar_filename :s.goals :s.assists :s.own_goals)
                  (h/from [:PeladaPlayerStats :s])
                  (h/join [:OrganizationPlayers :p] [:= :p.id :s.player_id])
                  (h/join [:Users :u] [:= :u.id :p.user_id])
                  (h/where [:= :s.pelada_id pelada-id])
                  (h/order-by [:s.goals :desc] [:s.assists :desc]))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map unqualify-row)
         (map (fn [row]
                {:player-id (:player_id row)
                 :user-id (:user_id row)
                 :name (:name row)
                 :avatar-filename (:avatar_filename row)
                 :goals (:goals row)
                 :assists (:assists row)
                 :own-goals (:own_goals row)}))
         vec)))
