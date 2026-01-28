(ns api-peladaapp.db.organization
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.helpers.misc :as misc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-organization :- s/Int
  [{:keys [name]} db]
  (-> (sql/insert! db :organizations {:name name})
      affected-rows-count
      int))

(s/defn get-organization [id db]
  (-> (sql/get-by-id db :organizations id) adapter.organization/db->model))

(s/defn update-organization :- s/Int
  [id {:keys [name]} db]
  (-> (sql/update! db :organizations {:name name} {:id id}) affected-rows-count))

(s/defn delete-organization :- s/Int
  [id db]
  (-> (sql/delete! db :organizations {:id id}) affected-rows-count))

(s/defn list-organizations [db limit offset]
  (->> (sql/query db ["select * from organizations order by id limit ? offset ?" limit offset])
       (map adapter.organization/db->model)))

(s/defn list-by-user [user-id db]
  (->> (sql/query db ["SELECT o.id, o.name, 'admin' as role
                       FROM Organizations o
                       JOIN OrganizationAdmins oa ON o.id = oa.organization_id
                       WHERE oa.user_id = ?
                       UNION
                       SELECT o.id, o.name, 'player' as role
                       FROM Organizations o
                       JOIN OrganizationPlayers op ON o.id = op.organization_id
                       WHERE op.user_id = ?
                       AND NOT EXISTS (SELECT 1 FROM OrganizationAdmins oa WHERE oa.organization_id = o.id AND oa.user_id = ?)"
                      user-id user-id user-id])
       (map misc/unamespace)))

(s/defn count-organizations :- s/Int
  [db]
  (-> (sql/query db ["select count(*) as count from organizations"]) first :count))

(s/defn get-statistics
  [id :- s/Int
   year :- s/Int
   db]
  (let [where-year (if (pos? year) " AND strftime('%Y', p.scheduled_at) = ?" "")
        base-params (if (pos? year) [id (str year)] [id])
        params (into [] (concat base-params base-params base-params))
        sql (str "
WITH RawParticipation AS (
    SELECT ml.player_id, m.pelada_id
    FROM MatchLineups ml
    JOIN Matches m ON ml.match_id = m.id
    JOIN Peladas p ON m.pelada_id = p.id
    WHERE p.organization_id = ? " where-year "

    UNION

    SELECT tp.player_id, m.pelada_id
    FROM TeamPlayers tp
    JOIN Teams t ON tp.team_id = t.id
    JOIN Matches m ON (m.home_team_id = t.id OR m.away_team_id = t.id)
    JOIN Peladas p ON m.pelada_id = p.id
    WHERE p.organization_id = ? " where-year "
      AND NOT EXISTS (SELECT 1 FROM MatchLineups sub_ml WHERE sub_ml.match_id = m.id)
),
PlayerParticipation AS (
    SELECT player_id, COUNT(DISTINCT pelada_id) as peladas_count
    FROM RawParticipation
    GROUP BY player_id
),
PlayerEvents AS (
    SELECT me.player_id, me.event_type, COUNT(*) as event_count
    FROM MatchEvents me
    JOIN Matches m ON me.match_id = m.id
    JOIN Peladas p ON m.pelada_id = p.id
    WHERE p.organization_id = ? " where-year "
    GROUP BY me.player_id, me.event_type
)
SELECT 
    pp.player_id, 
    u.name as player_name, 
    pp.peladas_count,
    pe.event_type,
    pe.event_count as count
FROM PlayerParticipation pp
JOIN OrganizationPlayers op ON pp.player_id = op.id
JOIN Users u ON op.user_id = u.id
LEFT JOIN PlayerEvents pe ON pp.player_id = pe.player_id
")]
    (sql/query db (into [sql] params) {:builder-fn rs/as-unqualified-lower-maps})))
