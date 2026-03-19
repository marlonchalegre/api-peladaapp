(ns api-peladaapp.db.pelada
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [api-peladaapp.adapters.user :as adapter.user]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.score :as logic.score]
   [medley.core :as medley.core]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count
  [result]
  (-> result vals first))

(s/defn insert-pelada :- s/Int
  [{:keys [organization-id scheduled-at num-teams players-per-team fixed-goalkeepers
           home-fixed-goalkeeper-id away-fixed-goalkeeper-id]}
   db]
  (let [row (cond-> {:organization_id organization-id :status "attendance"}
              scheduled-at (assoc :scheduled_at scheduled-at)
              num-teams (assoc :num_teams num-teams)
              players-per-team (assoc :players_per_team players-per-team)
              (some? fixed-goalkeepers) (assoc :fixed_goalkeepers (if fixed-goalkeepers 1 0))
              home-fixed-goalkeeper-id (assoc :home_fixed_goalkeeper_id home-fixed-goalkeeper-id)
              away-fixed-goalkeeper-id (assoc :away_fixed_goalkeeper_id away-fixed-goalkeeper-id))]
    (-> (sql/insert! db :peladas row)
        affected-rows-count)))

(s/defn get-pelada :- s/Any
  [id :- s/Int
   db]
  (-> (sql/get-by-id db :peladas id)
      adapter.pelada/db->model))

(s/defn update-pelada :- s/Int
  [id :- s/Int
   pelada
   db]
  (let [db-row (cond-> (medley.core/assoc-some {}
                                               :organization_id (:organization-id pelada)
                                               :scheduled_at (:scheduled-at pelada)
                                               :num_teams (:num-teams pelada)
                                               :players_per_team (:players-per-team pelada)
                                               :fixed_goalkeepers (when (some? (:fixed-goalkeepers pelada))
                                                                    (if (:fixed-goalkeepers pelada) 1 0))
                                               :status (:status pelada)
                                               :closed_at (:closed-at pelada)
                                               :timer_started_at (:timer-started-at pelada)
                                               :timer_accumulated_ms (:timer-accumulated-ms pelada)
                                               :timer_status (:timer-status pelada)
                                               :vote_ended_message_sent (when (some? (:vote-ended-message-sent pelada))
                                                                          (if (:vote-ended-message-sent pelada) 1 0))
                                               :vote_reminder_12h_sent (when (some? (:vote-reminder-12h-sent pelada))
                                                                         (if (:vote-reminder-12h-sent pelada) 1 0))
                                               :vote_reminder_23h_sent (when (some? (:vote-reminder-23h-sent pelada))
                                                                         (if (:vote-reminder-23h-sent pelada) 1 0)))
                 (contains? pelada :home-fixed-goalkeeper-id) (assoc :home_fixed_goalkeeper_id (:home-fixed-goalkeeper-id pelada))
                 (contains? pelada :away-fixed-goalkeeper-id) (assoc :away_fixed_goalkeeper_id (:away-fixed-goalkeeper-id pelada)))]
    (if (empty? db-row)
      1
      (-> (sql/update! db :peladas db-row {:id id})
          affected-rows-count))))

(s/defn delete-pelada :- s/Int
  [id :- s/Int
   db]
  (-> (sql/delete! db :peladas {:id id})
      affected-rows-count))

(s/defn list-peladas :- [s/Any]
  [organization-id :- s/Int
   limit :- s/Int
   offset :- s/Int
   db]
  (->> (sql/query db ["select * from peladas where organization_id = ? order by id desc limit ? offset ?" organization-id limit offset])
       (map adapter.pelada/db->model)))

(s/defn count-peladas :- s/Int
  [organization-id :- s/Int
   db]
  (-> (sql/query db ["select count(*) as count from peladas where organization_id = ?" organization-id])
      first
      :count))

(s/defn list-peladas-by-user :- [s/Any]
  [user-id :- s/Int
   limit :- s/Int
   offset :- s/Int
   db]
  (->> (sql/query db ["SELECT p.*, o.name as organization_name
                       FROM Peladas p
                       JOIN OrganizationPlayers op ON op.organization_id = p.organization_id
                       JOIN Organizations o ON o.id = p.organization_id
                       WHERE op.user_id = ?
                       ORDER BY
                         CASE
                           WHEN p.status = 'closed' AND p.closed_at > datetime('now', '-24 hours') THEN 1
                           WHEN p.status != 'closed' THEN 2
                           ELSE 3
                         END ASC,
                         p.scheduled_at DESC, p.id DESC
                       LIMIT ? OFFSET ?" user-id limit offset])
       (map adapter.pelada/db->model)))

(s/defn count-peladas-by-user :- s/Int
  [user-id :- s/Int
   db]
  (-> (sql/query db ["SELECT count(*) as count
                       FROM Peladas p
                       JOIN OrganizationPlayers op ON op.organization_id = p.organization_id
                       WHERE op.user_id = ?" user-id])
      first
      :count))

(s/defn list-peladas-for-vote-notification :- [s/Any]
  [db]
  (->> (sql/query db ["SELECT * FROM Peladas 
                       WHERE status = 'closed' 
                       AND vote_ended_message_sent = 0 
                       AND closed_at < datetime('now', '-24 hours')"])
       (map adapter.pelada/db->model)))

(s/defn list-peladas-for-vote-reminders :- [{:pelada s/Any :type s/Keyword}]
  [db]
  (let [rem-12h (->> (sql/query db ["SELECT * FROM Peladas 
                                     WHERE status = 'closed' 
                                     AND vote_reminder_12h_sent = 0 
                                     AND closed_at < datetime('now', '-12 hours')"])
                     (map (fn [p] {:pelada (adapter.pelada/db->model p) :type :12h})))
        rem-23h (->> (sql/query db ["SELECT * FROM Peladas 
                                     WHERE status = 'closed' 
                                     AND vote_reminder_23h_sent = 0 
                                     AND closed_at < datetime('now', '-23 hours')"])
                     (map (fn [p] {:pelada (adapter.pelada/db->model p) :type :23h})))]
    (concat rem-12h rem-23h)))

(s/defn get-pelada-full-details :- s/Any
  [pelada-id :- s/Int
   db]
  (if-let [pelada (get-pelada pelada-id db)]
    (let [organization-id (:organization-id pelada)
          attendance (db.attendance/list-attendance-by-pelada pelada-id db)
          attendance-map (into {} (map (juxt :player_id :status)) attendance)

          ;; If not in attendance mode, we only care about confirmed players
          all-players-in-org (db.player/list-players-by-organization organization-id db)
          all-org-players (if (= "attendance" (:status pelada))
                            all-players-in-org
                            (filter (fn [p] (= "confirmed" (get attendance-map (:id p))))
                                    all-players-in-org))

          all-users (map #(adapter.user/model->response % true) (db.user/list-users db 0 100000)) ;; Exclude email for privacy
          users-map (into {} (map (juxt :id identity)) all-users)
          teams (db.team/list-pelada-teams pelada-id db)
          team-players-raw (db.team/list-team-players-by-pelada pelada-id db)

          ;; Group team players by team ID
          team-players-grouped (group-by :team_id team-players-raw)

          ;; Add players to teams - Use all-players-in-org to avoid nulls if some team player is not 'confirmed'
          teams-with-players (map (fn [team]
                                    (assoc team :players (keep (fn [team-player]
                                                                 (when-let [player (first (filter #(= (:player_id team-player) (:id %)) all-players-in-org))]
                                                                   (let [user (get users-map (:user-id player))]
                                                                     (assoc player :user user :is_goalkeeper (:is_goalkeeper team-player)))))
                                                               (get team-players-grouped (:id team) []))))
                                  teams)

          ;; Identify players already assigned to teams
          assigned-player-ids (set (map :player_id team-players-raw))

          ;; Filter available players (not in any team for this pelada)
          available-players (filter (fn [p] (not (assigned-player-ids (:id p))))
                                    all-org-players)

          ;; Add user info to available players
          available-players-with-users (map (fn [player]
                                              (assoc player :user (get users-map (:user-id player))
                                                     :attendance-status (get attendance-map (:id player) "pending")))
                                            available-players)

          ;; Calculate normalized scores for all players in org
          player-ids (map :id all-players-in-org)
          scores-map (if (seq player-ids)
                       (logic.score/get-normalized-scores player-ids db)
                       {})]

      {:pelada pelada
       :teams teams-with-players
       :available-players available-players-with-users
       :scores scores-map
       :attendance (filter (fn [a] (some #(= (:player_id a) (:id %)) all-org-players))
                           (map (fn [a] (assoc a :player (let [p (first (filter #(= (:player_id a) (:id %)) all-players-in-org))]
                                                           (assoc p :user (get users-map (:user-id p))))))
                                attendance))
       :users-map users-map
       :org-players-map (into {} (map (juxt :id identity)) all-players-in-org)})
    (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"}))))
