(ns api-peladaapp.db.match-event
  (:require
   [api-peladaapp.adapters.match :as adapter.match]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.models.match :as models.match]
   [api-peladaapp.models.match-event :as models.match-event]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- unqualify-row [row]
  (into {}
        (map (fn [[k v]]
               (let [kw (if (keyword? k) (keyword (name k)) k)]
                 [kw v])))
        row))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn get-event :- (s/maybe models.match-event/MatchEvent)
  [id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :MatchEvents)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        adapter.match/db-event->model)))

(s/defn insert-event :- s/Uuid
  ([match-id player-id event-type db]
   (insert-event match-id player-id event-type nil nil db))
  ([match-id :- s/Uuid player-id :- s/Uuid event-type :- s/Str session-time-ms match-time-ms db]
   (let [row (cond-> {:match_id match-id
                      :player_id player-id
                      :event_type event-type}
               session-time-ms (assoc :session_time_ms session-time-ms)
               match-time-ms (assoc :match_time_ms match-time-ms))
         query (-> (h/insert-into :MatchEvents)
                   (h/values [row])
                   (h/returning :id))]
     (:id (jdbc/execute-one! db (hsql/format query) opts)))))

(s/defn list-events-by-pelada :- [models.match-event/MatchEvent]
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :e.id :e.match_id :e.player_id :e.event_type :e.created_at :e.session_time_ms :e.match_time_ms)
                  (h/from [:MatchEvents :e])
                  (h/join [:Matches :m] [:= :m.id :e.match_id])
                  (h/where [:= :m.pelada_id pelada-id])
                  (h/order-by :e.id))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.match/db-event->model))))

(s/defn delete-last-event :- s/Int
  [match-id :- s/Uuid player-id :- s/Uuid event-type :- s/Str db]
  (let [sub-query (-> (h/select :id)
                      (h/from :MatchEvents)
                      (h/where [:= :match_id match-id]
                               [:= :player_id player-id]
                               [:= :event_type event-type])
                      (h/order-by [:id :desc])
                      (h/limit 1))
        query (-> (h/delete-from :MatchEvents)
                  (h/where [:in :id sub-query]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        (as-> res (if (map? res) 1 0)))))

(s/defn list-player-stats-by-pelada :- [models.match/PlayerStats]
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :s.player_id :p.user_id :u.name :u.avatar_filename :s.goals :s.assists :s.own_goals)
                  (h/from [:PeladaPlayerStats :s])
                  (h/join [:OrganizationPlayers :p] [:= :p.id :s.player_id])
                  (h/join [:Users :u] [:= :u.id :p.user_id])
                  (h/where [:= :s.pelada_id pelada-id])
                  (h/order-by [:s.goals :desc] [:s.assists :desc]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
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
