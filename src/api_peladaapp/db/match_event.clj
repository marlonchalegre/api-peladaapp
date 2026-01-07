(ns api-peladaapp.db.match-event
  (:require
   [api-peladaapp.adapters.match :as adapter.match]
   [api-peladaapp.models.match :as models.match]
   [api-peladaapp.models.match-event :as models.match-event]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(defn- unqualify-row [row]
  (into {}
        (map (fn [[k v]]
               (let [kw (if (keyword? k) (keyword (name k)) k)]
                 [kw v])))
        row))

(s/defn get-event :- (s/maybe models.match-event/MatchEvent)
  [id :- s/Int db]
  (-> (sql/get-by-id db :matchevents id)
      adapter.match/db-event->model))

(s/defn insert-event :- s/Int
  [match-id :- s/Int player-id :- s/Int event-type :- s/Str db]
  (sql/insert! db :matchevents {:match_id match-id
                                :player_id player-id
                                :event_type event-type})
  (-> (jdbc/execute-one! db ["select last_insert_rowid() as id"]) :id int))

(s/defn list-events-by-pelada :- [models.match-event/MatchEvent]
  [pelada-id :- s/Int db]
  (->> (jdbc/execute! db
                      ["select e.id, e.match_id, e.player_id, e.event_type, e.created_at
                     from MatchEvents e
                     join Matches m on m.id = e.match_id
                     where m.pelada_id = ?
                     order by e.id" pelada-id])
       (map adapter.match/db-event->model)))

(s/defn delete-last-event :- s/Int
  [match-id :- s/Int player-id :- s/Int event-type :- s/Str db]
  (-> (jdbc/execute-one! db
                         ["delete from MatchEvents where id in (
                                select id from MatchEvents
                                where match_id = ? and player_id = ? and event_type = ?
                                order by id desc limit 1
                              )" match-id player-id event-type])
      affected-rows-count))

(s/defn list-player-stats-by-pelada :- [models.match/PlayerStats]
  [pelada-id :- s/Int db]
  (->> (jdbc/execute! db
                      ["select e.player_id, p.user_id, u.name,
                                 sum(case when e.event_type='goal' then 1 else 0 end)      as goals,
                                 sum(case when e.event_type='assist' then 1 else 0 end)    as assists,
                                 sum(case when e.event_type='own_goal' then 1 else 0 end) as own_goals
                           from MatchEvents e
                           join Matches m on m.id = e.match_id
                           join OrganizationPlayers p on p.id = e.player_id
                           join Users u on u.id = p.user_id
                          where m.pelada_id = ?
                          group by e.player_id, p.user_id, u.name
                          order by goals desc, assists desc" pelada-id])
       (map unqualify-row)
       vec))