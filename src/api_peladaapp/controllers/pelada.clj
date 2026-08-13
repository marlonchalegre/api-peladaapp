(ns api-peladaapp.controllers.pelada
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.match-event :as db.match-event]
   [api-peladaapp.db.match-lineup :as db.match-lineup]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.schedule :as db.schedule]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.transaction :as db.transaction]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.helpers.time :as helpers.time]
   [api-peladaapp.logic.notifications :as notifications]
   [api-peladaapp.logic.pelada :as pelada.logic]
   [api-peladaapp.models.pelada :as models.pelada]
   [clojure.tools.logging :as log]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(defn- auto-create-teams!
  [pelada-id team-count db]
  (when (pos? team-count)
    (->> (range 1 (inc team-count))
         (map (fn [index]
                {:pelada-id pelada-id
                 :name (str "Time " index)}))
         (run! #(db.team/insert-team % db)))))

(defn- fetch-team-ids
  [pelada-id db]
  (->> (db.team/list-pelada-teams pelada-id db)
       (map :id)
       vec))

(defn- persist-match-plan!
  [pelada-id match-plan db]
  (->> (pelada.logic/match-plan->rows pelada-id match-plan)
       (run! #(db.match/insert-match % db))))

(defn- seed-lineups-from-teams!
  [pelada-id db]
  (->> (db.match/list-matches-by-pelada pelada-id db)
       (map :id)
       (run! #(db.match-lineup/ensure-seeded % db))))

(s/defn get-schedule-preview
  [pelada-id :- s/Uuid matches-per-team :- s/Int db]
  (let [teams (db.team/list-pelada-teams pelada-id db)
        team-ids (mapv :id teams)
        team-count (count team-ids)]
    (if (< team-count 2)
      {:matches [] :is_from_format false}
      (let [random-plan (try (pelada.logic/schedule-matches-for-start team-ids matches-per-team)
                             (catch Exception e (log/error e "[ERROR] failed to gen random plan:") []))]
        {:matches random-plan
         :random_matches random-plan
         :is_from_format false}))))

(s/defn save-schedule-plan
  [pelada-id :- s/Uuid matches db]
  (jdbc/with-transaction [tx db]
    (let [teams (db.team/list-pelada-teams pelada-id tx)
          team-ids (mapv :id teams)
          team-set (set team-ids)]

      ;; Validate that all teams in matches belong to this pelada
      (doseq [match matches]
        (when-not (and (contains? team-set (:home match))
                       (contains? team-set (:away match)))
          (throw (ex-info "One or more teams in the schedule do not belong to this pelada"
                          {:type :bad-request
                           :message "Invalid teams in schedule plan. Please refresh and try again."}))))

      ;; Delete old plan
      (db.schedule/delete-match-plans-by-pelada pelada-id tx)

      ;; Insert new plan
      (doseq [[idx match] (map-indexed vector matches)]
        (db.schedule/insert-match-plan {:pelada-id pelada-id
                                        :home-team-id (:home match)
                                        :away-team-id (:away match)
                                        :sequence (inc idx)}
                                       tx))
      {:status "success"})))

(s/defn get-schedule-plan
  [pelada-id :- s/Uuid db]
  (let [plans (db.schedule/list-match-plans-by-pelada pelada-id db)]
    (map (fn [p]
           (let [row (misc/unamespace p)]
             {:home (:home_team_id row)
              :away (:away_team_id row)
              :sequence (:sequence row)}))
         plans)))

(s/defn create-pelada :- models.pelada/Pelada
  "Create pelada and optionally seed default teams. Returns pelada model."
  [pelada :- models.pelada/Pelada
   db]
  (let [result (jdbc/with-transaction [tx db]
                 (let [pelada-id (db.pelada/insert-pelada pelada tx)]
                   (when-let [team-count (:num-teams pelada)]
                     (auto-create-teams! pelada-id team-count tx))
                   (db.pelada/get-pelada pelada-id tx)))]
    ;; WAHA Notification - Async outside transaction
    (future
      (try
        (let [org-id (:organization-id result)
              pelada-id (:id result)]
          (notifications/send-notification! org-id :new-pelada {:pelada-id pelada-id :scheduled-at (:scheduled-at result) :confirmed-players [] :notify-casual-players (:notify-casual-players result)} db))

        (catch Exception e (log/error e "Error sending new pelada notification:"))))
    result))

(s/defn get-pelada :- models.pelada/Pelada
  [pelada-id :- s/Uuid db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (if (nil? pelada)
      (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"}))
      pelada)))

(defn- enforce-players-per-team!
  [pelada-id players-per-team db]
  (let [teams (db.team/list-pelada-teams pelada-id db)
        team-players (db.team/list-team-players-by-pelada pelada-id db)
        team-players-grouped (group-by :team_id team-players)]
    (doseq [team teams]
      (let [players (get team-players-grouped (:id team) [])
            excess (- (count players) players-per-team)]
        (when (pos? excess)
          ;; Prioritize keeping goalkeepers, then remove last ones (by original order/ID)
          (let [to-remove (->> players
                               (sort-by (fn [p] [(:is_goalkeeper p) (:id p)]) #(compare %2 %1))
                               (drop players-per-team))]
            (doseq [p to-remove]
              (db.team/remove-player-from-team (:team_id p) (:player_id p) db))))))))

(s/defn update-pelada :- models.pelada/Pelada
  [pelada-id :- s/Uuid pelada :- models.pelada/Pelada db]
  (jdbc/with-transaction [tx db]
    (let [old-pelada (db.pelada/get-pelada pelada-id tx)
          rows (db.pelada/update-pelada pelada-id pelada tx)]
      (when (zero? rows)
        (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"})))

      (doseq [gk-key [:home-fixed-goalkeeper-id :away-fixed-goalkeeper-id]]
        (when (contains? pelada gk-key)
          (let [old-gk-id (get old-pelada gk-key)
                new-gk-id (get pelada gk-key)]
            (when (not= old-gk-id new-gk-id)
              (when (some? new-gk-id)
                (db.attendance/update-voting-enabled pelada-id new-gk-id false tx))
              (when (some? old-gk-id)
                (db.attendance/update-voting-enabled pelada-id old-gk-id true tx))))))

      ;; Handle player redistribution if players-per-team decreased
      (when-let [new-count (:players-per-team pelada)]
        (when (and (:players-per-team old-pelada)
                   (< new-count (:players-per-team old-pelada)))
          (enforce-players-per-team! pelada-id new-count tx)))

      (db.pelada/get-pelada pelada-id tx))))

(s/defn delete-pelada :- s/Int
  [pelada-id :- s/Uuid db]
  (let [rows (db.pelada/delete-pelada pelada-id db)]
    (if (zero? rows)
      (throw (ex-info "Pelada not found" {:type :not-found :message "Pelada not found"}))
      rows)))

(s/defn list-peladas
  [organization-id :- s/Uuid db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        peladas (db.pelada/list-peladas organization-id per-page offset db)
        total-count (db.pelada/count-peladas organization-id db)]
    (pagination/with-pagination-headers peladas total-count page per-page)))

(s/defn list-all-peladas
  [db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        peladas (db.pelada/list-all-peladas per-page offset db)
        total-count (db.pelada/count-all-peladas db)]
    (pagination/with-pagination-headers peladas total-count page per-page)))

(s/defn list-peladas-by-user
  [user-id :- s/Uuid db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        peladas (db.pelada/list-peladas-by-user user-id per-page offset db)
        total-count (db.pelada/count-peladas-by-user user-id db)]
    (pagination/with-pagination-headers peladas total-count page per-page)))

(s/defn begin-pelada :- {:matches-created s/Int}
  "Generate matches for a pelada, transition it to running, and seed lineups."
  [pelada-id :- s/Uuid db & [opts]]
  (let [result (try
                 (jdbc/with-transaction [tx db]
                   (let [matches-per-team (:matches_per_team (or opts {}))
                         pelada (db.pelada/get-pelada pelada-id tx)
                         saved-plan (db.schedule/list-match-plans-by-pelada pelada-id tx)
                         match-plan (if (seq saved-plan)
                                      (map (fn [p] {:home (:home_team_id p)
                                                    :away (:away_team_id p)})
                                           saved-plan)
                                      (let [team-ids (->> (fetch-team-ids pelada-id tx)
                                                          (pelada.logic/ensure-startable pelada))]
                                        (pelada.logic/schedule-matches-for-start team-ids matches-per-team)))]
                     (persist-match-plan! pelada-id match-plan tx)
                     (db.pelada/update-pelada pelada-id {:status "running"} tx)
                     (seed-lineups-from-teams! pelada-id tx)
                     {:pelada pelada :matches-created (count match-plan)}))
                 (catch Exception e
                   (log/error e "CRITICAL ERROR in begin-pelada transaction:")
                   (throw e)))]

    ;; WAHA Notification - Async outside transaction
    (future
      (try
        (let [pelada (:pelada result)
              teams (db.team/list-pelada-teams pelada-id db)
              team-players (db.team/list-team-players-with-names-by-pelada pelada-id db)]
          (notifications/send-notification! (:organization-id pelada) :start {:teams teams :team-players team-players} db))
        (catch Exception e (log/error e "Error sending start notification:"))))

    {:matches-created (:matches-created result)}))

(s/defn start-pelada-timer :- models.pelada/Pelada
  [pelada-id :- s/Uuid db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (if (= "running" (:timer-status pelada))
      pelada
      (let [now (str (java.time.Instant/now))]
        (db.pelada/update-pelada pelada-id {:timer-status "running" :timer-started-at now} db)
        (db.pelada/get-pelada pelada-id db)))))

(s/defn pause-pelada-timer :- models.pelada/Pelada
  [pelada-id :- s/Uuid db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (if (not= "running" (:timer-status pelada))
      pelada
      (let [now (java.time.Instant/now)
            started-at (helpers.time/->instant (:timer-started-at pelada))
            elapsed (.toMillis (java.time.Duration/between started-at now))
            new-accumulated (+ (or (:timer-accumulated-ms pelada) 0) elapsed)]
        (db.pelada/update-pelada pelada-id {:timer-status "paused"
                                            :timer-started-at nil
                                            :timer-accumulated-ms new-accumulated} db)
        (db.pelada/get-pelada pelada-id db)))))

(s/defn reset-pelada-timer :- models.pelada/Pelada
  [pelada-id :- s/Uuid db]
  (db.pelada/update-pelada pelada-id {:timer-status "stopped"
                                      :timer-started-at nil
                                      :timer-accumulated-ms 0} db)
  (db.pelada/get-pelada pelada-id db))

(s/defn close-pelada :- models.pelada/Pelada
  [pelada-id :- s/Uuid db]
  (let [pelada (try
                 (jdbc/with-transaction [tx db]
                   (db.match/finish-all-by-pelada pelada-id tx)
                   ;; Ensure timer is paused when closing
                   (pause-pelada-timer pelada-id tx)
                   (db.pelada/update-pelada pelada-id {:status "closed" :closed-at (str (java.time.Instant/now))} tx)
                   (db.pelada/get-pelada pelada-id tx))
                 (catch Exception e
                   (log/error e "CRITICAL ERROR in close-pelada transaction:")
                   (throw e)))]

    ;; WAHA Notification - Async outside transaction
    (future
      (try
        (let [matches (db.match/list-matches-by-pelada pelada-id db)
              teams (db.team/list-pelada-teams pelada-id db)
              events (db.match-event/list-events-by-pelada pelada-id db)
              lineups (db.match-lineup/list-match-lineups-by-pelada pelada-id db)
              team-players (db.team/list-team-players-with-names-by-pelada pelada-id db)
              org-players (db.player/list-players-by-organization (:organization-id pelada) db)
              all-players (distinct (concat team-players (map (fn [p] {:player_id (:id p) :player_name (:user-name p)}) org-players)))]
          (notifications/send-notification! (:organization-id pelada) :end
                                            {:pelada pelada
                                             :matches matches
                                             :teams teams
                                             :events events
                                             :lineups lineups
                                             :team-players all-players}
                                            db))
        (catch Exception e (log/error e "Error sending close/reminder notification:"))))
    pelada))

(s/defn get-pelada-dashboard-data
  [pelada-id :- s/Uuid
   user-id :- s/Uuid
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        organization-id (:organization-id pelada)
        matches (db.match/list-matches-by-pelada pelada-id db)
        teams (db.team/list-pelada-teams pelada-id db)
        organization-players (db.player/list-players-by-organization organization-id db)
        match-events (db.match-event/list-events-by-pelada pelada-id db)
        player-stats (try (db.match-event/list-player-stats-by-pelada pelada-id db) (catch Exception _ nil))
        team-players (db.team/list-team-players-by-pelada pelada-id db)
        match-lineups (db.match-lineup/list-match-lineups-by-pelada pelada-id db)
        attendance (db.attendance/list-attendance-by-pelada pelada-id db)

        is-admin (db.admin/is-user-admin-of-organization? user-id (:organization-id pelada) db)
        transactions (if is-admin (db.transaction/list-transactions-by-pelada pelada-id db) [])

        referenced-user-ids (set (remove nil? (concat
                                               [(:creator-id pelada)]
                                               (map :user-id organization-players)
                                               (map :user-id match-events)
                                               (map :created-by transactions))))
        users (db.user/get-users-by-ids db (vec referenced-user-ids))]
    {:pelada pelada
     :is-admin is-admin
     :matches matches
     :teams teams
     :users users
     :organization-players organization-players
     :match-events match-events
     :player-stats player-stats
     :team-players team-players
     :match-lineups match-lineups
     :transactions transactions
     :attendance attendance}))

(s/defn get-pelada-full-details-controller
  [pelada-id :- s/Uuid
   user-id :- s/Uuid
   db]
  (let [pelada-data (db.pelada/get-pelada-full-details pelada-id db)
        pelada (:pelada pelada-data)
        is-admin (db.admin/is-user-admin-of-organization? user-id (:organization-id pelada) db)
        all-org-players (:org-players-map pelada-data)
        has-schedule-plan (seq (db.schedule/list-match-plans-by-pelada pelada-id db))

        current-player (some-> (filter #(= user-id (:user-id %)) (vals all-org-players)) first)
        player-id (:id current-player)
        voting-info (if (and (= "closed" (:status pelada)) player-id)
                      (pelada.logic/get-voting-info pelada-id player-id db)
                      nil)
        transactions (if is-admin (db.transaction/list-transactions-by-pelada pelada-id db) [])]
    (assoc pelada-data
           :is-admin is-admin
           :has-schedule-plan (boolean has-schedule-plan)
           :pelada-transactions transactions
           :voting-info voting-info)))
