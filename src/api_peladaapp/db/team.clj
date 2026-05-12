(ns api-peladaapp.db.team
  (:require
   [api-peladaapp.adapters.team :as adapter.team]
   [api-peladaapp.helpers.sql :as hsql]
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

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-team :- s/Int
  [{:keys [pelada-id name]}
   db]
  (let [query (-> (h/insert-into :Teams)
                  (h/values [{:pelada_id pelada-id :name name}])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn get-team [id db]
  (let [query (-> (h/select :*)
                  (h/from :Teams)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        adapter.team/db->model)))

(s/defn update-team :- s/Int
  [id team db]
  (let [query (-> (h/update :Teams)
                  (h/set (select-keys team [:name]))
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn delete-team :- s/Int
  [id db]
  (let [query (-> (h/delete-from :Teams)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn list-pelada-teams [pelada-id db]
  (let [query (-> (h/select :*)
                  (h/from :Teams)
                  (h/where [:= :pelada_id pelada-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.team/db->model))))

(s/defn validate-player-belongs-to-pelada-org :- (s/maybe s/Bool)
  "Validates if a player belongs to the same organization as the pelada of the team"
  [team-id player-id db]
  (let [query (-> (h/select 1)
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Teams :t] [:= :t.id team-id])
                  (h/join [:Peladas :p] [:= :p.id :t.pelada_id])
                  (h/where [:= :op.id player-id] [:= :op.organization_id :p.organization_id]))]
    (some? (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn validate-player-not-in-another-team-of-same-pelada :- (s/maybe s/Bool)
  "Validates if a player is not already in another team of the same pelada"
  [team-id player-id db]
  (let [query (-> (h/select 1)
                  (h/from [:TeamPlayers :tp])
                  (h/join [:Teams :t] [:= :t.id :tp.team_id])
                  (h/where [:and
                            [:= :t.pelada_id (-> (h/select :pelada_id) (h/from :Teams) (h/where [:= :id team-id]))]
                            [:= :tp.player_id player-id]]))]
    (nil? (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn validate-team-not-full :- (s/maybe s/Bool)
  "Validates if the team has not reached the player limit.
   Note: Fixed goalkeepers are global and not in TeamPlayers."
  [team-id db]
  (let [query (-> (h/select [:p.players_per_team :max_players] [[:count :tp.player_id] :current_count])
                  (h/from [:Teams :t])
                  (h/join [:Peladas :p] [:= :p.id :t.pelada_id])
                  (h/left-join [:TeamPlayers :tp] [:= :tp.team_id :t.id])
                  (h/where [:= :t.id team-id])
                  (h/group-by :p.players_per_team))
        result (jdbc/execute-one! db (hsql/format query) opts)]
    (if (and (:max_players result)
             (>= (:current_count result) (:max_players result)))
      false
      true)))

(s/defn add-player-to-team :- s/Int
  ([team-id player-id db]
   (add-player-to-team team-id player-id false db))
  ([team-id player-id is-goalkeeper db]
   (when-not (validate-player-belongs-to-pelada-org team-id player-id db)
     (throw (ex-info "Player does not belong to the pelada's organization"
                     {:type :validation-error
                      :message "Player does not belong to the pelada's organization"
                      :team-id team-id
                      :player-id player-id})))
   (when-not (validate-player-not-in-another-team-of-same-pelada team-id player-id db)
     (throw (ex-info "Player is already in a team for this pelada"
                     {:type :validation-error
                      :message "Player is already in a team for this pelada"
                      :team-id team-id
                      :player-id player-id})))
   ;; Note: is-goalkeeper is ignored for TeamPlayers validation now because they are global
   (when-not (validate-team-not-full team-id db)
     (throw (ex-info "Team is full"
                     {:type :validation-error
                      :message "Team is full"
                      :team-id team-id})))
   (let [query (-> (h/insert-into :TeamPlayers)
                   (h/values [{:team_id team-id :player_id player-id :is_goalkeeper (boolean is-goalkeeper)}]))]
     (-> (jdbc/execute-one! db (hsql/format query) opts)
         affected-rows-count))))

(s/defn add-team-players-batch! :- s/Any
  "Inserts multiple team players in a single batch. Skips validations (caller must ensure consistency)."
  [assignments db]
  (when (seq assignments)
    (let [query (-> (h/insert-into :TeamPlayers)
                    (h/values (map (fn [a] (update a :is_goalkeeper boolean)) assignments)))]
      (jdbc/execute! db (hsql/format query) opts))))

(s/defn remove-player-from-team :- s/Int
  [team-id player-id db]
  (let [query (-> (h/delete-from :TeamPlayers)
                  (h/where [:and [:= :team_id team-id] [:= :player_id player-id]]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn clear-teams-players :- s/Int
  "Removes all players from all Teams of a specific pelada"
  [pelada-id db]
  (let [query (-> (h/delete-from :TeamPlayers)
                  (h/where [:in :team_id (-> (h/select :id) (h/from :Teams) (h/where [:= :pelada_id pelada-id]))]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn list-team-players [team-id db]
  (let [query (-> (h/select :*)
                  (h/from :TeamPlayers)
                  (h/where [:= :team_id team-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map unqualify-row)
         (map (fn [m]
                {:team-id (:team_id m)
                 :player-id (:player_id m)
                 :is-goalkeeper (if (boolean? (:is_goalkeeper m)) (:is_goalkeeper m) (not= 0 (:is_goalkeeper m)))})))))

(s/defn list-team-players-by-pelada [pelada-id db]
  (let [query (-> (h/select :tp.* [:t.name :team_name] :t.pelada_id)
                  (h/from [:TeamPlayers :tp])
                  (h/join [:Teams :t] [:= :tp.team_id :t.id])
                  (h/where [:= :t.pelada_id pelada-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map unqualify-row)
         (map (fn [m]
                (assoc m :is_goalkeeper (if (boolean? (:is_goalkeeper m)) (:is_goalkeeper m) (not= 0 (:is_goalkeeper m))))))
         vec)))

(s/defn list-team-players-with-names-by-pelada [pelada-id db]
  (let [query (-> (h/select :tp.* [:t.name :team_name] [:u.name :player_name] :u.position)
                  (h/from [:TeamPlayers :tp])
                  (h/join [:Teams :t] [:= :tp.team_id :t.id])
                  (h/join [:OrganizationPlayers :op] [:= :tp.player_id :op.id])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/where [:= :t.pelada_id pelada-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map unqualify-row)
         (map (fn [m]
                (assoc m :is_goalkeeper (if (boolean? (:is_goalkeeper m)) (:is_goalkeeper m) (not= 0 (:is_goalkeeper m))))))
         vec)))

(s/defn did-player-participate-in-pelada? :- s/Bool
  [pelada-id :- s/Int player-id :- s/Int db]
  (let [query (-> (h/select 1)
                  (h/from [:TeamPlayers :tp])
                  (h/join [:Teams :t] [:= :t.id :tp.team_id])
                  (h/where [:= :t.pelada_id pelada-id] [:= :tp.player_id player-id]))]
    (some? (jdbc/execute-one! db (hsql/format query) opts))))
