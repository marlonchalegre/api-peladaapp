(ns api-peladaapp.db.organization
  (:require
   [api-peladaapp.adapters.organization :as adapter.organization]
   [api-peladaapp.helpers.misc :as misc]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-organization :- s/Int
  [org db]
  (let [row (adapter.organization/model->db org)]
    (-> (sql/insert! db :organizations row)
        affected-rows-count
        int)))

(s/defn get-organization [id db]
  (let [query "SELECT o.*, 
                      wc.api_url as waha_api_url, wc.instance as waha_instance, wc.group_id as waha_group_id,
                      wc.enabled as waha_enabled, wc.start_msg_enabled as waha_start_msg_enabled,
                      wc.end_msg_enabled as waha_end_msg_enabled, wc.attendance_reminder_enabled as waha_attendance_reminder_enabled,
                      wc.vote_reminder_enabled as waha_vote_reminder_enabled, wc.vote_ended_msg_enabled as waha_vote_ended_msg_enabled
               FROM Organizations o
               LEFT JOIN OrganizationWahaConfigs wc ON o.id = wc.organization_id
               WHERE o.id = ?"
        result (sql/query db [query id])]
    (some-> result first adapter.organization/db->model)))

(s/defn update-organization :- s/Int
  [id org db]
  (jdbc/with-transaction [tx db]
    (let [org-row (select-keys org [:name])
          waha-row (-> org
                       (select-keys [:waha-api-url :waha-instance :waha-group-id :waha-enabled :waha-start-msg-enabled :waha-end-msg-enabled :waha-attendance-reminder-enabled :waha-vote-reminder-enabled :waha-vote-ended-msg-enabled])
                       (update-keys (comp keyword #(str/replace % "waha-" "") name))
                       (update-keys (comp keyword #(str/replace % "-" "_") name)))]
      (when (seq org-row)
        (sql/update! tx :organizations org-row {:id id}))
      (when (seq waha-row)
        (let [waha-row (assoc waha-row :organization_id id)
              exists? (first (sql/query tx ["SELECT 1 FROM OrganizationWahaConfigs WHERE organization_id = ?" id]))]
          (if exists?
            (sql/update! tx :organizationwahaconfigs waha-row {:organization_id id})
            (sql/insert! tx :organizationwahaconfigs waha-row))))
      1)))

(s/defn delete-organization :- s/Int
  [id db]
  (-> (sql/delete! db :organizations {:id id}) affected-rows-count))

(s/defn list-organizations [db limit offset]
  (->> (sql/query db ["select * from organizations order by id limit ? offset ?" limit offset])
       (map adapter.organization/db->model)))

(s/defn list-all-organizations [db]
  (let [query "SELECT o.*, 
                      wc.api_url as waha_api_url, wc.instance as waha_instance, wc.group_id as waha_group_id,
                      wc.enabled as waha_enabled, wc.start_msg_enabled as waha_start_msg_enabled,
                      wc.end_msg_enabled as waha_end_msg_enabled, wc.attendance_reminder_enabled as waha_attendance_reminder_enabled,
                      wc.vote_reminder_enabled as waha_vote_reminder_enabled, wc.vote_ended_msg_enabled as waha_vote_ended_msg_enabled
               FROM Organizations o
               LEFT JOIN OrganizationWahaConfigs wc ON o.id = wc.organization_id"]
    (->> (sql/query db [query])
         (map adapter.organization/db->model))))

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
        manual-stats-params (if (pos? year) [id year] [id])
        params (into [] (concat
                         base-params ;; RawParticipation 1
                         base-params ;; RawParticipation 2
                         base-params ;; PlayerEvents (MatchEvents part)
                         manual-stats-params ;; PlayerEvents (goals part)
                         manual-stats-params ;; PlayerEvents (assists part)
                         manual-stats-params ;; PlayerEvents (own_goals part)
                         manual-stats-params ;; AllPlayers (ManualStats part)
                         base-params ;; PlayerRatings
                         ))
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

    UNION ALL

    SELECT ms.player_id, 'goal' as event_type, ms.goals as event_count
    FROM ManualStats ms
    WHERE ms.organization_id = ? " (if (pos? year) " AND ms.year = ?" "") " AND ms.goals > 0

    UNION ALL

    SELECT ms.player_id, 'assist' as event_type, ms.assists as event_count
    FROM ManualStats ms
    WHERE ms.organization_id = ? " (if (pos? year) " AND ms.year = ?" "") " AND ms.assists > 0

    UNION ALL

    SELECT ms.player_id, 'own_goal' as event_type, ms.own_goals as event_count
    FROM ManualStats ms
    WHERE ms.organization_id = ? " (if (pos? year) " AND ms.year = ?" "") " AND ms.own_goals > 0
),
AllPlayers AS (
    SELECT player_id FROM PlayerParticipation
    UNION
    SELECT player_id FROM ManualStats WHERE organization_id = ? " (if (pos? year) " AND year = ?" "") "
),
PlayerRatings AS (
    SELECT v.target_id as player_id, AVG(v.stars) as avg_rating
    FROM Votes v
    JOIN Peladas p ON v.pelada_id = p.id
    WHERE p.organization_id = ? " where-year "
    GROUP BY v.target_id
)
SELECT 
    ap.player_id, 
    u.id as user_id,
    u.name as player_name, 
    u.position as player_position,
    u.avatar_filename,
    COALESCE(pp.peladas_count, 0) as peladas_count,
    COALESCE(pr.avg_rating, 0.0) as avg_rating,
    pe.event_type,
    SUM(pe.event_count) as count
FROM AllPlayers ap
JOIN OrganizationPlayers op ON ap.player_id = op.id
JOIN Users u ON op.user_id = u.id
LEFT JOIN PlayerParticipation pp ON ap.player_id = pp.player_id
LEFT JOIN PlayerEvents pe ON ap.player_id = pe.player_id
LEFT JOIN PlayerRatings pr ON ap.player_id = pr.player_id
GROUP BY ap.player_id, u.id, u.name, u.position, u.avatar_filename, pp.peladas_count, pr.avg_rating, pe.event_type
")]
    (sql/query db (into [sql] params) {:builder-fn rs/as-unqualified-lower-maps})))
