(ns api-peladaapp.controllers.vote
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.logic.vote :as vote.logic]
   [api-peladaapp.models.vote :as models.vote]
   [schema.core :as s]))

(s/defn cast-vote :- models.vote/Vote
  [{:keys [pelada-id] :as vote} :- models.vote/Vote db]
  ;; Validate pelada voting eligibility
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (vote.logic/validate-voting-eligibility pelada))
  ;; Validate individual vote
  (vote.logic/validate-vote vote)
  (let [id (db.vote/insert-vote vote db)]
    (db.vote/get-vote id db)))

(s/defn batch-cast-votes :- models.vote/BatchVote
  "Cast multiple votes at once. Replaces any existing votes by this voter."
  [pelada-id :- s/Uuid voter-id :- s/Uuid votes :- [{:target-id s/Uuid :stars s/Int}] db]
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
  [pelada-id :- s/Uuid db]
  (db.vote/list-votes-by-pelada pelada-id db))

(s/defn get-voting-info :- models.vote/VotingInfo
  "Get voting eligibility info for a voter in a pelada."
  [pelada-id :- s/Uuid user-id :- s/Uuid db]
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
                                         (db.team/did-player-participate-in-pelada? pelada-id voter-player-id db)))]

          (when (and (not participated) (not is-admin))
            (throw (ex-info "Player did not participate in this pelada"
                            {:type :forbidden :message "Only players who participated can vote"})))

          ;; Get all players who participated (were in teams) with their names and stats
          (let [eligible-players-raw (db.vote/list-eligible-players-for-voting pelada-id voter-player-id db)
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

(s/defn get-voting-status :- models.vote/VotingStatus
  [pelada-id :- s/Uuid db]
  (let [votes (db.vote/list-votes-by-pelada pelada-id db)
        ;; Get all participants (potential voters)
        participants-raw (db.vote/list-pelada-participants pelada-id db)
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
                             :avatar-filename (:avatar_filename p)
                             :has-voted (boolean (voted-ids (:player-id p)))})
                          participants)]
    {:voters voter-status
     :total-eligible (count participants)
     :total-voted (count voted-ids)}))

(s/defn get-voting-results :- models.vote/VotingResults
  [pelada-id :- s/Uuid user-id :- s/Uuid db]
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
                                     (db.team/did-player-participate-in-pelada? pelada-id voter-player-id db)))]

      (when (and participated
                 (not is-admin)
                 (not (db.vote/has-voter-voted? pelada-id voter-player-id db)))
        (throw (ex-info "You must vote to see the results"
                        {:type :forbidden
                         :message "Você precisa votar para ter acesso aos resultados da pelada."})))

      (let [status (get-voting-status pelada-id db)
            votes (db.vote/list-votes-by-pelada pelada-id db)
            ;; Get stats for all players
            participants-raw (db.vote/list-pelada-participants pelada-id db)
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
