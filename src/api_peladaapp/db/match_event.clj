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
  (-> (sql/insert! db :matchevents {:match_id match-id
                                    :player_id player-id
                                    :event_type event-type})
      affected-rows-count
      int))

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
                      ["select s.player_id, p.user_id, u.name, s.goals, s.assists, s.own_goals
                           from PeladaPlayerStats s
                           join OrganizationPlayers p on p.id = s.player_id
                           join Users u on u.id = p.user_id
                          where s.pelada_id = ?
                          order by s.goals desc, s.assists desc" pelada-id])
       (map unqualify-row)
       (map (fn [row]
              {:player-id (:player_id row)
               :user-id (:user_id row)
               :name (:name row)
               :goals (:goals row)
               :assists (:assists row)
               :own-goals (:own_goals row)}))
       vec))