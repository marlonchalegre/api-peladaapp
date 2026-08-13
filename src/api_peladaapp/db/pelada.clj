(ns api-peladaapp.db.pelada
  (:require
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.score :as logic.score]
   [honey.sql.helpers :as h]
   [medley.core :as medley.core]
   [next.jdbc :as jdbc]
   [schema.core :as s])
  (:import (java.sql Timestamp)
           (java.time Duration Instant OffsetDateTime)
           (java.time.temporal ChronoUnit)))

(s/defn insert-pelada :- s/Uuid
  [pelada :- {:organization-id s/Uuid
              (s/optional-key :scheduled-at) (s/maybe s/Str)
              (s/optional-key :num-teams) (s/maybe s/Int)
              (s/optional-key :players-per-team) (s/maybe s/Int)
              (s/optional-key :fixed-goalkeepers) (s/maybe s/Bool)
              (s/optional-key :home-fixed-goalkeeper-id) (s/maybe s/Uuid)
              (s/optional-key :away-fixed-goalkeeper-id) (s/maybe s/Uuid)
              (s/optional-key :notify-casual-players) (s/maybe s/Bool)}
   db]
  (let [{:keys [organization-id scheduled-at num-teams players-per-team fixed-goalkeepers
                home-fixed-goalkeeper-id away-fixed-goalkeeper-id notify-casual-players]} pelada
        row (cond-> {:organization_id organization-id :status [:cast "attendance" :pelada_status]}
              scheduled-at (assoc :scheduled_at [[:cast (helpers.time/to-utc-timestamp-str scheduled-at) :timestamp]])
              num-teams (assoc :num_teams num-teams)
              players-per-team (assoc :players_per_team players-per-team)
              (some? fixed-goalkeepers) (assoc :fixed_goalkeepers (boolean fixed-goalkeepers))
              home-fixed-goalkeeper-id (assoc :home_fixed_goalkeeper_id home-fixed-goalkeeper-id)
              away-fixed-goalkeeper-id (assoc :away_fixed_goalkeeper_id away-fixed-goalkeeper-id)
              (some? notify-casual-players) (assoc :notify_casual_players (boolean notify-casual-players)))
        query (-> (h/insert-into :Peladas)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))

(s/defn get-pelada :- s/Any
  [id :- s/Uuid
   db]
  (let [query (-> (h/select :p.* [:o.name :organization_name])
                  (h/from [:Peladas :p])
                  (h/join [:Organizations :o] [:= :o.id :p.organization_id])
                  (h/where [:= :p.id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        adapter.pelada/db->model)))

(s/defn update-pelada :- s/Int
  [id :- s/Uuid
   pelada
   db]
  (let [db-row (cond-> (medley.core/assoc-some {}
                                               :organization_id (:organization-id pelada)
                                               :scheduled_at (when (:scheduled-at pelada) [[:cast (helpers.time/to-utc-timestamp-str (:scheduled-at pelada)) :timestamp]])
                                               :num_teams (:num-teams pelada)
                                               :players_per_team (:players-per-team pelada)
                                               :fixed_goalkeepers (when (some? (:fixed-goalkeepers pelada))
                                                                    (boolean (:fixed-goalkeepers pelada)))
                                               :status (when (:status pelada) [[:cast (:status pelada) :pelada_status]])
                                               :closed_at (when (:closed-at pelada) [[:cast (helpers.time/to-utc-timestamp-str (:closed-at pelada)) :timestamp]])
                                               :timer_started_at (when (:timer-started-at pelada) [[:cast (helpers.time/to-utc-timestamp-str (:timer-started-at pelada)) :timestamp]])
                                               :timer_accumulated_ms (:timer-accumulated-ms pelada)
                                               :timer_status (when (:timer-status pelada) [[:cast (:timer-status pelada) :timer_status]]))
                 (contains? pelada :home-fixed-goalkeeper-id) (assoc :home_fixed_goalkeeper_id (:home-fixed-goalkeeper-id pelada))
                 (contains? pelada :away-fixed-goalkeeper-id) (assoc :away_fixed_goalkeeper_id (:away-fixed-goalkeeper-id pelada))
                 (contains? pelada :notify-casual-players) (assoc :notify_casual_players (boolean (:notify-casual-players pelada))))]
    (if (empty? db-row)
      1
      (let [query (-> (h/update :Peladas)
                      (h/set db-row)
                      (h/where [:= :id id]))]
        (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
            hsql/affected-rows-count)))))

(s/defn delete-pelada :- s/Int
  [id :- s/Uuid
   db]
  (let [query (-> (h/delete-from :Peladas)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn list-peladas :- [s/Any]
  [organization-id :- s/Uuid
   limit :- s/Int
   offset :- s/Int
   db]
  (let [query (-> (h/select :p.* [:o.name :organization_name])
                  (h/from [:Peladas :p])
                  (h/join [:Organizations :o] [:= :o.id :p.organization_id])
                  (h/where [:= :p.organization_id organization-id])
                  (h/order-by [:p.scheduled_at :desc] [:p.id :desc])
                  (h/limit limit)
                  (h/offset offset))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map adapter.pelada/db->model))))

(s/defn count-peladas :- s/Int
  [organization-id :- s/Uuid
   db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Peladas)
                  (h/where [:= :organization_id organization-id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        :count
        int)))

(s/defn list-all-peladas :- [s/Any]
  [limit :- s/Int
   offset :- s/Int
   db]
  (let [query (-> (h/select :p.* [:o.name :organization_name])
                  (h/from [:Peladas :p])
                  (h/join [:Organizations :o] [:= :o.id :p.organization_id])
                  (h/order-by [:p.scheduled_at :desc] [:p.id :desc])
                  (h/limit limit)
                  (h/offset offset))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map adapter.pelada/db->model))))

(s/defn count-all-peladas :- s/Int
  [db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Peladas))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        :count
        int)))

(s/defn list-peladas-by-user :- [s/Any]
  [user-id :- s/Uuid
   limit :- s/Int
   offset :- s/Int
   db]
  (let [one-day-ago (-> (Instant/now) (.minus (Duration/ofDays 1)) Timestamp/from)
        query (-> (h/select :p.* [:o.name :organization_name] [:a.status :user_attendance_status])
                  (h/from [:Peladas :p])
                  (h/join [:OrganizationPlayers :op] [:= :op.organization_id :p.organization_id])
                  (h/join [:Organizations :o] [:= :o.id :p.organization_id])
                  (h/left-join [:Attendance :a] [:and [:= :a.pelada_id :p.id] [:= :a.player_id :op.id]])
                  (h/where [:= :op.user_id user-id])
                  (h/order-by
                   [[:case
                     [:and [:= :p.status [:cast "closed" :pelada_status]] [:> :p.closed_at one-day-ago]] 1
                     [:!= :p.status [:cast "closed" :pelada_status]] 2
                     :else 3] :asc]
                   [:p.scheduled_at :desc]
                   [:p.id :desc])
                  (h/limit limit)
                  (h/offset offset))
        results (jdbc/execute! db (hsql/format query) hsql/opts)]
    (map adapter.pelada/db->model results)))

(s/defn count-peladas-by-user :- s/Int
  [user-id :- s/Uuid
   db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from [:Peladas :p])
                  (h/join [:OrganizationPlayers :op] [:= :op.organization_id :p.organization_id])
                  (h/where [:= :op.user_id user-id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        :count
        int)))

(s/defn list-peladas-for-vote-notification :- [s/Any]
  [db]
  (let [one-day-ago (-> (Instant/now) (.minus (Duration/ofDays 1)) Timestamp/from)
        query (-> (h/select :p.* [:o.name :organization_name])
                  (h/from [:Peladas :p])
                  (h/join [:Organizations :o] [:= :o.id :p.organization_id])
                  (h/where [:and
                            [:= :p.status [:cast "closed" :pelada_status]]
                            [:not-exists (-> (h/select 1)
                                             (h/from :PeladaReminders)
                                             (h/where [:= :pelada_id :p.id] [:= :type [:cast "vote_ended" :reminder_type]]))]
                            [:< :p.closed_at one-day-ago]]))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map adapter.pelada/db->model))))

(s/defn list-peladas-for-vote-reminders :- [{:pelada s/Any :type s/Keyword}]
  [db]
  (let [now (OffsetDateTime/now)
        rem-30m-start (.minus now 90 ChronoUnit/MINUTES)
        rem-30m-end (.minus now 30 ChronoUnit/MINUTES)
        rem-12h-start (.minus now 13 ChronoUnit/HOURS)
        rem-12h-end (.minus now 12 ChronoUnit/HOURS)
        rem-23h-start (.minus now 24 ChronoUnit/HOURS)
        rem-23h-end (.minus now 23 ChronoUnit/HOURS)
        fetch-reminders (fn [type start end]
                          (let [query (-> (h/select :p.* [:o.name :organization_name])
                                          (h/from [:Peladas :p])
                                          (h/join [:Organizations :o] [:= :o.id :p.organization_id])
                                          (h/where [:and
                                                    [:= :p.status [:cast "closed" :pelada_status]]
                                                    [:not-exists (-> (h/select 1)
                                                                     (h/from :PeladaReminders)
                                                                     (h/where [:= :pelada_id :p.id] [:= :type [:cast (name type) :reminder_type]]))]]
                                                   [:>= :p.closed_at [[:cast (helpers.time/to-utc-timestamp-str start) :timestamp]]]
                                                   [:< :p.closed_at [[:cast (helpers.time/to-utc-timestamp-str end) :timestamp]]]))]
                            (->> (jdbc/execute! db (hsql/format query) hsql/opts)
                                 (map (fn [p] {:pelada (adapter.pelada/db->model p) :type type})))))]
    (concat (fetch-reminders :vote_30m rem-30m-start rem-30m-end)
            (fetch-reminders :vote_12h rem-12h-start rem-12h-end)
            (fetch-reminders :vote_23h rem-23h-start rem-23h-end))))

(s/defn get-pelada-full-details :- s/Any
  [pelada-id :- s/Uuid
   db]
  (if-let [pelada (get-pelada pelada-id db)]
    (let [organization-id (:organization-id pelada)
          attendance (db.attendance/list-attendance-by-pelada pelada-id db)
          attendance-map (into {} (map (fn [a] [(misc/as-uuid (:player_id a)) {:status (:status a)
                                                                               :updated_at (:updated_at a)
                                                                               :voting_enabled (if (contains? a :voting_enabled)
                                                                                                 (boolean (:voting_enabled a))
                                                                                                 true)}]))
                               attendance)
          ;; If not in attendance mode, we only care about confirmed players
          all-players-in-org (db.player/list-players-by-organization organization-id db)
          players-map (into {} (map (juxt :id identity)) all-players-in-org)

          ;; Build users-map from players in the organization instead of fetching ALL users
          users-map (into {} (map (fn [p]
                                    [(:user-id p)
                                     {:id (:user-id p)
                                      :name (:user-name p)
                                      :username (:user-username p)
                                      :position (:user-position p)
                                      :avatar_filename (:user-avatar-filename p)}]))
                          all-players-in-org)

          all-org-players (if (= "attendance" (:status pelada))
                            all-players-in-org
                            (filter (fn [p] (= "confirmed" (:status (get attendance-map (misc/as-uuid (:id p))))))
                                    all-players-in-org))

          teams (db.team/list-pelada-teams pelada-id db)
          team-players-raw (db.team/list-team-players-by-pelada pelada-id db)

          ;; Group team players by team ID
          team-players-grouped (group-by :team_id team-players-raw)

          ;; Add players to teams - Use players-map to avoid O(N*M) filter
          teams-with-players (map (fn [team]
                                    (assoc team :players (keep (fn [team-player]
                                                                 (when-let [player (get players-map (:player_id team-player))]
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
                                              (let [att (get attendance-map (misc/as-uuid (:id player)))]
                                                (assoc player :user (get users-map (:user-id player))
                                                       :attendance-status (:status att "pending")
                                                       :attendance-updated-at (some-> (:updated_at att) helpers.time/->instant str))))
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
       :attendance (filter (fn [a] (contains? players-map (:player_id a)))
                           (map (fn [a] (assoc a :player (let [p (get players-map (:player_id a))]
                                                           (assoc p :user (get users-map (:user-id p))))))
                                attendance))
       :users-map users-map
       :org-players-map players-map})
    (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"}))))

(s/defn count-peladas-in-month-by-org :- s/Int
  [organization-id :- s/Uuid year :- s/Int month :- s/Int db]
  (let [start-date (java.time.LocalDate/of year month 1)
        end-date (.plusMonths start-date 1)
        query (-> (h/select [[:count :*] :count])
                  (h/from :Peladas)
                  (h/where [:= :organization_id organization-id]
                           [:>= :scheduled_at [:cast (str start-date " 00:00:00") :timestamp]]
                           [:< :scheduled_at [:cast (str end-date " 00:00:00") :timestamp]]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        :count
        int)))

