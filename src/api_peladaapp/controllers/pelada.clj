(ns api-peladaapp.controllers.pelada
  (:require
   [api-peladaapp.adapters.match :as adapter.match]
   [api-peladaapp.adapters.pelada :as adapter.pelada]
   [api-peladaapp.adapters.player :as adapter.player]
   [api-peladaapp.adapters.team :as adapter.team]
   [api-peladaapp.adapters.vote :as adapter.vote]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.match-event :as db.match-event]
   [api-peladaapp.db.match-lineup :as db.match-lineup]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.schedule :as db.schedule]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.logic.pelada :as pelada.logic]
   [api-peladaapp.models.pelada :as models.pelada]
   [api-peladaapp.responses.pelada :as responses.pelada]
   [clojure.data.json :as json]
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
  [pelada-id :- s/Int matches-per-team :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        org-id (:organization-id pelada)
        teams (db.team/list-pelada-teams pelada-id db)
        team-ids (mapv :id teams)
        team-count (count team-ids)]
    (if (< team-count 2)
      {:matches [] :is_from_format false}
      (let [format (db.schedule/get-organization-schedule-format org-id team-count matches-per-team db)
            random-plan (try (pelada.logic/schedule-matches-for-start team-ids matches-per-team)
                             (catch Exception _ []))]
        (if format
          (let [format-data (json/read-str (:OrganizationScheduleFormats/format_data format))
                template-plan (map (fn [[h-idx a-idx]]
                                     {:home (nth team-ids h-idx)
                                      :away (nth team-ids a-idx)})
                                   format-data)]
            {:matches template-plan
             :template_matches template-plan
             :random_matches random-plan
             :is_from_format true})
          {:matches random-plan
           :random_matches random-plan
           :is_from_format false})))))

(s/defn save-schedule-plan
  [pelada-id :- s/Int matches-per-team :- s/Int matches db]
  (jdbc/with-transaction [tx db]
    (let [pelada (db.pelada/get-pelada pelada-id tx)
          org-id (:organization-id pelada)
          teams (db.team/list-pelada-teams pelada-id tx)
          team-ids (mapv :id teams)
          team-count (count team-ids)]

      ;; Delete old plan
      (db.schedule/delete-match-plans-by-pelada pelada-id tx)

      ;; Insert new plan
      (doseq [[idx match] (map-indexed vector matches)]
        (db.schedule/insert-match-plan {:pelada-id pelada-id
                                        :home-team-id (:home match)
                                        :away-team-id (:away match)
                                        :sequence (inc idx)}
                                       tx))

      ;; Save/Update format for organization
      (let [format-data (map (fn [match]
                               [(.indexOf team-ids (:home match))
                                (.indexOf team-ids (:away match))])
                             matches)]
        (db.schedule/upsert-format {:organization-id org-id
                                    :team-count team-count
                                    :matches-per-team matches-per-team
                                    :format-data (json/write-str format-data)}
                                   tx))
      {:status "success"})))

(s/defn get-schedule-plan
  [pelada-id :- s/Int db]
  (let [plans (db.schedule/list-match-plans-by-pelada pelada-id db)]
    (map (fn [p]
           {:home (:PeladaMatchPlans/home_team_id p)
            :away (:PeladaMatchPlans/away_team_id p)
            :sequence (:PeladaMatchPlans/sequence p)})
         plans)))

(s/defn create-pelada :- models.pelada/Pelada
  "Create pelada and optionally seed default teams. Returns pelada model."
  [pelada :- models.pelada/Pelada
   db]
  (jdbc/with-transaction [tx db]
    (let [pelada-id (db.pelada/insert-pelada pelada tx)]
      (when-let [team-count (:num-teams pelada)]
        (auto-create-teams! pelada-id team-count tx))
      (db.pelada/get-pelada pelada-id tx))))

(s/defn get-pelada :- models.pelada/Pelada
  [pelada-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (if (nil? pelada)
      (throw (ex-info nil {:type :not-found :message "Pelada not found"}))
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
  [pelada-id :- s/Int pelada :- models.pelada/Pelada db]
  (jdbc/with-transaction [tx db]
    (let [old-pelada (db.pelada/get-pelada pelada-id tx)
          rows (db.pelada/update-pelada pelada-id pelada tx)]
      (when (zero? rows)
        (throw (ex-info nil {:type :not-found :message "Pelada not found"})))

      ;; Handle player redistribution if players-per-team decreased
      (when-let [new-count (:players-per-team pelada)]
        (when (and (:players-per-team old-pelada)
                   (< new-count (:players-per-team old-pelada)))
          (enforce-players-per-team! pelada-id new-count tx)))

      (db.pelada/get-pelada pelada-id tx))))

(s/defn delete-pelada :- s/Int
  [pelada-id :- s/Int db]
  (let [rows (db.pelada/delete-pelada pelada-id db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Pelada not found"}))
      rows)))

(s/defn list-peladas
  [organization-id :- s/Int db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        peladas (db.pelada/list-peladas organization-id per-page offset db)
        total-count (db.pelada/count-peladas organization-id db)]
    (pagination/with-pagination-headers peladas total-count page per-page)))

(s/defn list-peladas-by-user
  [user-id :- s/Int db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (- page 1) per-page)
        peladas (db.pelada/list-peladas-by-user user-id per-page offset db)
        total-count (db.pelada/count-peladas-by-user user-id db)]
    (pagination/with-pagination-headers peladas total-count page per-page)))

(s/defn begin-pelada :- responses.pelada/PeladaBeginResponse
  "Generate matches for a pelada, transition it to running, and seed lineups."
  [pelada-id :- s/Int db & [opts]]
  (jdbc/with-transaction [tx db]
    (let [matches-per-team (:matches_per_team (or opts {}))
          pelada (db.pelada/get-pelada pelada-id tx)
          saved-plan (db.schedule/list-match-plans-by-pelada pelada-id tx)
          match-plan (if (seq saved-plan)
                       (map (fn [p] {:home (:PeladaMatchPlans/home_team_id p)
                                     :away (:PeladaMatchPlans/away_team_id p)})
                            saved-plan)
                       (let [team-ids (->> (fetch-team-ids pelada-id tx)
                                           (pelada.logic/ensure-startable pelada))]
                         (pelada.logic/schedule-matches-for-start team-ids matches-per-team)))]
      (persist-match-plan! pelada-id match-plan tx)
      (db.pelada/update-pelada pelada-id {:status "running"} tx)
      (seed-lineups-from-teams! pelada-id tx)
      {:matches_created (count match-plan)})))

(s/defn close-pelada :- models.pelada/Pelada
  [pelada-id :- s/Int db]
  (jdbc/with-transaction [tx db]
    (db.match/finish-all-by-pelada pelada-id tx)
    (db.pelada/update-pelada pelada-id {:status "closed" :closed-at (str (java.time.Instant/now))} tx)
    (db.pelada/get-pelada pelada-id tx)))

(s/defn get-pelada-dashboard-data :- responses.pelada/PeladaDashboardResponse
  [pelada-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)
        organization-id (:organization-id pelada)
        matches (db.match/list-matches-by-pelada pelada-id db)
        teams (db.team/list-pelada-teams pelada-id db)
        users (map #(dissoc % :password :email) (db.user/list-users db 0 1000000))
        organization-players (db.player/list-players-by-organization organization-id db)
        match-events (db.match-event/list-events-by-pelada pelada-id db)
        player-stats (try (db.match-event/list-player-stats-by-pelada pelada-id db) (catch Exception _ nil))
        team-players (db.team/list-team-players-by-pelada pelada-id db)
        match-lineups (db.match-lineup/list-match-lineups-by-pelada pelada-id db)

        ;; Transform team-players into a map
        team-players-map (into {} (map (fn [[tid tps]]
                                         [tid (map (fn [tp]
                                                     {:team_id (:team_id tp)
                                                      :player_id (:player_id tp)
                                                      :is_goalkeeper false})
                                                   tps)])
                                       (group-by :team_id team-players)))

        ;; Transform match-lineups into a map of match-id -> team-id -> players
        match-lineups-map (reduce (fn [acc {:keys [match_id team_id] :as lineup}]
                                    (assoc-in acc [match_id team_id] (conj (get-in acc [match_id team_id] []) lineup)))
                                  {}
                                  match-lineups)]
    {:pelada (adapter.pelada/model->response pelada)
     :matches (map adapter.match/model->response matches)
     :teams (map adapter.team/model->response teams)
     :users users
     :organization_players (map adapter.player/model->response organization-players)
     :match_events (map adapter.match/event->response match-events)
     :player_stats (when player-stats (map adapter.match/stats->response player-stats))
     :team_players_map team-players-map
     :match_lineups_map match-lineups-map}))

(s/defn get-pelada-full-details-controller :- responses.pelada/PeladaFullDetailsResponse
  [pelada-id :- s/Int
   user-id :- s/Int
   db]
  (let [pelada-data (db.pelada/get-pelada-full-details pelada-id db)
        pelada (:pelada pelada-data)
        is-admin (db.admin/is-user-admin-of-organization? user-id (:organization-id pelada) db)
        all-org-players (:org-players-map pelada-data)
        has-schedule-plan (seq (db.schedule/list-match-plans-by-pelada pelada-id db))

        current-player (some-> (filter #(= user-id (:user-id %)) (vals all-org-players)) first)
        player-id (:id current-player)
        voting-info (if (and (= "closed" (:status pelada)) player-id)
                      (adapter.vote/voting-info-model->response
                       (pelada.logic/get-voting-info pelada-id player-id db))
                      nil)

        ;; Map models back to response format
        mapped-pelada (assoc (adapter.pelada/model->response pelada)
                             :is_admin is-admin
                             :has_schedule_plan (boolean has-schedule-plan))
        mapped-teams (map (fn [team]
                            (assoc (adapter.team/model->response team)
                                   :players (map (fn [p] (assoc (adapter.player/model->response p)
                                                                :user (:user p)
                                                                :is_goalkeeper false))
                                                 (:players team))))
                          (:teams pelada-data))

        mapped-available (map (fn [p]
                                (assoc (adapter.player/model->response p)
                                       :user (:user p)
                                       :attendance_status (:attendance-status p)))
                              (:available-players pelada-data))
        mapped-attendance (map (fn [a]
                                 (assoc a :player (assoc (adapter.player/model->response (:player a))
                                                         :user (get-in a [:player :user]))))
                               (:attendance pelada-data))]
    (-> pelada-data
        (assoc :pelada mapped-pelada
               :teams mapped-teams
               :available_players mapped-available
               :attendance mapped-attendance
               :voting_info voting-info))))
