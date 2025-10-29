(ns api-100folego.db.match-event
  (:require [next.jdbc :as jdbc]
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

(s/defn insert-event :- s/Int
  [match-id :- s/Int player-id :- s/Int event-type :- s/Str db]
  (-> (sql/insert! (db) :matchevents {:match_id match-id
                                      :player_id player-id
                                      :event_type event-type})
      affected-rows-count))

(s/defn list-events-by-pelada :- [s/Any]
  [pelada-id :- s/Int db]
  (with-open [conn (jdbc/get-connection (db))]
    (jdbc/execute! conn
                   ["select e.id, e.match_id, e.player_id, e.event_type, e.created_at
                     from MatchEvents e
                     join Matches m on m.id = e.match_id
                     where m.pelada_id = ?
                     order by e.id" pelada-id])))

(s/defn delete-last-event :- s/Int
  [match-id :- s/Int player-id :- s/Int event-type :- s/Str db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (jdbc/execute-one! conn
                            ["delete from MatchEvents where id in (
                                select id from MatchEvents
                                where match_id = ? and player_id = ? and event_type = ?
                                order by id desc limit 1
                              )" match-id player-id event-type])
        affected-rows-count)))

(s/defn list-player-stats-by-pelada :- [s/Any]
  [pelada-id :- s/Int db]
  (with-open [conn (jdbc/get-connection (db))]
    (->> (jdbc/execute! conn
                        ["select e.player_id,
                                 sum(case when e.event_type='goal' then 1 else 0 end)      as goals,
                                 sum(case when e.event_type='assist' then 1 else 0 end)    as assists,
                                 sum(case when e.event_type='own_goal' then 1 else 0 end) as own_goals
                           from MatchEvents e
                           join Matches m on m.id = e.match_id
                          where m.pelada_id = ?
                          group by e.player_id
                          order by goals desc, assists desc" pelada-id])
         (map unqualify-row)
         vec)))
