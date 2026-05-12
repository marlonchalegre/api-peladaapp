(ns api-peladaapp.controllers.vote
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.vote :as vote.logic]
   [api-peladaapp.models.vote :as models.vote]
   [api-peladaapp.responses.vote :as responses.vote]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn cast-vote :- models.vote/Vote
  [{:keys [pelada-id] :as vote} :- models.vote/Vote db]
  ;; Validate pelada voting eligibility
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (vote.logic/validate-voting-eligibility pelada))
  ;; Validate individual vote
  (vote.logic/validate-vote vote)
  (let [id (db.vote/insert-vote vote db)]
    (db.vote/get-vote id db)))

(s/defn batch-cast-votes :- responses.vote/BatchVoteResponse
  "Cast multiple votes at once. Replaces any existing votes by this voter."
  [pelada-id :- s/Int voter-id :- s/Int votes :- [{:target-id s/Int :stars s/Int}] db]
  ;; Validate pelada voting eligibility
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (vote.logic/validate-voting-eligibility pelada))

  ;; Validate that all target players have voting enabled
  (let [attendance (db.attendance/list-attendance-by-pelada pelada-id db)
        disabled-ids (set (map :player_id (filter #(= false (boolean (:voting_enabled %))) attendance)))]
    (doseq [v votes]
      (when (contains? disabled-ids (:target-id v))
        (throw (ex-info "Target player has voting disabled"
                        {:type :bad-request
                         :message "Cannot vote for a player who has voting disabled for this pelada"})))))

  ;; Delete existing votes by this voter
  (db.vote/delete-votes-by-voter pelada-id voter-id db)
  ;; Insert new votes in batch
  (let [full-votes (map (fn [vote]
                          {:pelada-id pelada-id
                           :voter-id voter-id
                           :target-id (:target-id vote)
                           :stars (:stars vote)})
                        votes)]
    (doseq [fv full-votes]
      (vote.logic/validate-vote fv))
    (db.vote/insert-votes-batch full-votes db))
  {:votes-cast (count votes)})

(s/defn list-votes :- [models.vote/Vote]
  [pelada-id :- s/Int db]
  (db.vote/list-votes-by-pelada pelada-id db))

(s/defn get-voting-info :- responses.vote/VotingInfoResponse
  "Get voting eligibility info for a voter in a pelada."
  [pelada-id :- s/Int user-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (try
      (vote.logic/validate-voting-eligibility pelada)
      (let [org-id (:organization-id pelada)
            is-admin (db.admin/is-user-admin-of-organization? user-id org-id db)
            voter-player (db.player/get-org-player-by-user-id user-id org-id db)
            voter-player-id (:id voter-player)]

        (when (and (not is-admin) (not voter-player-id))
          (throw (ex-info "User is not a player in this organization"
                          {:type :forbidden :message "User is not a player in this organization"})))

        ;; Verify voter participated in the pelada (was in a team)
        (let [participated (boolean (and voter-player-id
                                         (seq (let [q (-> (h/select 1)
                                                          (h/from [:TeamPlayers :tp])
                                                          (h/join [:Teams :t] [:= :t.id :tp.team_id])
                                                          (h/where [:= :t.pelada_id pelada-id] [:= :tp.player_id voter-player-id]))]
                                                (jdbc/execute! db (hsql/format q))))))]

          (when (and (not participated) (not is-admin))
            (throw (ex-info "Player did not participate in this pelada"
                            {:type :forbidden :message "Only players who participated can vote"})))

          ;; Get all players who participated (were in teams) with their names and stats
          (let [where-clause (cond-> [:and [:in :op.id (-> (h/select :player_id)
                                                           (h/from [:TeamPlayers :sub_tp])
                                                           (h/join [:Teams :sub_t] [:= :sub_t.id :sub_tp.team_id])
                                                           (h/where [:= :sub_t.pelada_id pelada-id]))]]
                               voter-player-id (conj [:!= :op.id voter-player-id]))
                query (-> (h/select [:op.id :player_id] [:u.id :user_id] :u.name :u.position :u.avatar_filename
                                    [[:coalesce :pa.voting_enabled true] :voting_enabled]
                                    [[:coalesce :s.goals 0] :goals]
                                    [[:coalesce :s.assists 0] :assists]
                                    [[:coalesce :s.own_goals 0] :own_goals])
                          (h/from [:OrganizationPlayers :op])
                          (h/join [:Users :u] [:= :u.id :op.user_id])
                          (h/left-join [:Attendance :pa] [:and [:= :pa.player_id :op.id] [:= :pa.pelada_id pelada-id]])
                          (h/left-join [:PeladaPlayerStats :s] [:and [:= :s.player_id :op.id] [:= :s.pelada_id pelada-id]])
                          (h/where where-clause))
                eligible-players-raw (jdbc/execute! db (hsql/format query) opts)
                eligible-players (mapv (fn [p]
                                         (let [up (misc/unamespace p)]
                                           {:player-id (:player_id up)
                                            :user-id (:user_id up)
                                            :name (:name up)
                                            :avatar-filename (:avatar_filename up)
                                            :position (:position up)
                                            :voting-enabled (let [v (:voting_enabled up)]
                                                              (if (boolean? v) v true))
                                            :goals (int (or (:goals up) 0))
                                            :assists (int (or (:assists up) 0))
                                            :own-goals (int (or (:own_goals up) 0))}))
                                       eligible-players-raw)
                has-voted (and voter-player-id (db.vote/has-voter-voted? pelada-id voter-player-id db))
                current-votes (if (and voter-player-id has-voted)
                                (db.vote/list-votes-by-voter pelada-id voter-player-id db)
                                [])]
            {:can-vote participated
             :has-voted has-voted
             :eligible-players eligible-players
             :current-votes current-votes
             :voter-player-id voter-player-id})))
      (catch Exception e
        (let [data (ex-data e)]
          {:can-vote false
           :has-voted false
           :eligible-players []
           :message (or (:message data) (.getMessage e))})))))

(s/defn get-voting-status
  [pelada-id :- s/Int db]
  (let [votes (db.vote/list-votes-by-pelada pelada-id db)
        ;; Get all participants (potential voters)
        query (-> (h/select [:op.id :player_id] [:u.id :user_id] :u.name :u.position :u.avatar_filename)
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :u.id :op.user_id])
                  (h/where [:in :op.id (-> (h/select :player_id)
                                           (h/from [:TeamPlayers :sub_tp])
                                           (h/join [:Teams :sub_t] [:= :sub_t.id :sub_tp.team_id])
                                           (h/where [:= :sub_t.pelada_id pelada-id]))]))
        participants-raw (jdbc/execute! db (hsql/format query) opts)
        participants (mapv (fn [p]
                             (let [up (misc/unamespace p)]
                               {:player-id (:player_id up)
                                :user-id (:user_id up)
                                :name (:name up)
                                :avatar-filename (:avatar_filename up)
                                :position (:position up)}))
                           participants-raw)
        voted-ids (set (map :voter-id votes))
        voter-status (map (fn [p]
                            {:player-id (:player-id p)
                             :user-id (:user-id p)
                             :name (:name p)
                             :avatar-filename (:avatar-filename p)
                             :has-voted (boolean (voted-ids (:player-id p)))})
                          participants)]
    {:voters voter-status
     :total-eligible (count participants)
     :total-voted (count voted-ids)}))

(s/defn get-voting-results :- responses.vote/VotingResultsResponse
  [pelada-id :- s/Int user-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (when (vote.logic/voting-open? pelada)
      (throw (ex-info "Voting still in progress"
                      {:type :bad-request
                       :message "Results are only available after the voting period ends (24h after close)."})))

    (let [org-id (:organization-id pelada)
          is-admin (db.admin/is-user-admin-of-organization? user-id org-id db)
          voter-player (db.player/get-org-player-by-user-id user-id org-id db)
          voter-player-id (:id voter-player)
          participated (boolean (and voter-player-id
                                     (seq (let [q (-> (h/select 1)
                                                      (h/from [:TeamPlayers :tp])
                                                      (h/join [:Teams :t] [:= :t.id :tp.team_id])
                                                      (h/where [:= :t.pelada_id pelada-id] [:= :tp.player_id voter-player-id]))]
                                            (jdbc/execute! db (hsql/format q))))))]

      (when (and participated
                 (not is-admin)
                 (not (db.vote/has-voter-voted? pelada-id voter-player-id db)))
        (throw (ex-info "You must vote to see the results"
                        {:type :forbidden
                         :message "Você precisa votar para ter acesso aos resultados da pelada."})))

      (let [status (get-voting-status pelada-id db)
            votes (db.vote/list-votes-by-pelada pelada-id db)
            ;; Get stats for all players
            query (-> (h/select [:op.id :player_id] [:u.id :user_id] :u.name :u.position :u.avatar_filename
                                [[:coalesce :s.goals 0] :goals]
                                [[:coalesce :s.assists 0] :assists]
                                [[:coalesce :s.own_goals 0] :own_goals])
                      (h/from [:OrganizationPlayers :op])
                      (h/join [:Users :u] [:= :u.id :op.user_id])
                      (h/left-join [:PeladaPlayerStats :s] [:and [:= :s.player_id :op.id] [:= :s.pelada_id pelada-id]])
                      (h/where [:in :op.id (-> (h/select :player_id)
                                               (h/from [:TeamPlayers :sub_tp])
                                               (h/join [:Teams :sub_t] [:= :sub_t.id :sub_tp.team_id])
                                               (h/where [:= :sub_t.pelada_id pelada-id]))]))
            participants-raw (jdbc/execute! db (hsql/format query) opts)
            participants (mapv (fn [p]
                                 (let [up (misc/unamespace p)]
                                   {:player-id (:player_id up)
                                    :user-id (:user_id up)
                                    :name (:name up)
                                    :avatar-filename (:avatar_filename up)
                                    :position (:position up)
                                    :goals (int (or (:goals up) 0))
                                    :assists (int (or (:assists up) 0))
                                    :own-goals (int (or (:own_goals up) 0))}))
                               participants-raw)

            ;; Calculate average stars per player
            votes-by-target (group-by :target-id votes)
            player-scores (map (fn [p]
                                 (let [p-votes (get votes-by-target (:player-id p) [])
                                       avg (if (seq p-votes)
                                             (double (/ (reduce + (map :stars p-votes)) (count p-votes)))
                                             0.0)]
                                   (assoc p :average-stars avg)))
                               participants)

            ;; Sort for awards
            mvp (->> player-scores
                     (sort-by :average-stars >)) ;; Show all in full ranking
            strikers (->> player-scores
                          (filter #(> (:goals %) 0))
                          (sort-by :goals >)
                          (take 3))
            garcoms (->> player-scores
                         (filter #(> (:assists %) 0))
                         (sort-by :assists >)
                         (take 3))]
        (assoc status
               :mvp mvp
               :striker strikers
               :garcom garcoms
               :organization-id (:organization-id pelada)
               :organization-name (:organization-name pelada))))))
