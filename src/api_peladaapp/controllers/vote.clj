(ns api-peladaapp.controllers.vote
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.logic.vote :as vote.logic]
   [api-peladaapp.models.vote :as models.vote]
   [api-peladaapp.responses.vote :as responses.vote]
   [next.jdbc.sql :as sql]
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

(s/defn batch-cast-votes :- responses.vote/BatchVoteResponse
  "Cast multiple votes at once. Replaces any existing votes by this voter."
  [pelada-id :- s/Int voter-id :- s/Int votes :- [{:target-id s/Int :stars s/Int}] db]
  ;; Validate pelada voting eligibility
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (vote.logic/validate-voting-eligibility pelada))
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

(s/defn compute-normalized-score :- responses.vote/NormalizedScoreResponse
  "Normalize a player's average stars (1..5) into 1..10 scale."
  [pelada-id :- s/Int player-id :- s/Int db]
  (let [votes (db.vote/list-votes-for-player pelada-id player-id db)
        res (vote.logic/normalized-score player-id votes)]
    {:player-id (:player-id res)
     :score (:score res)}))

(s/defn get-voting-info :- responses.vote/VotingInfoResponse
  "Get voting eligibility info for a voter in a pelada."
  [pelada-id :- s/Int user-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (try
      (vote.logic/validate-voting-eligibility pelada)
      (let [org-id (:organization-id pelada)
            voter-player (db.player/get-org-player-by-user-id user-id org-id db)
            voter-player-id (:id voter-player)]
        (when-not voter-player-id
          (throw (ex-info "User is not a player in this organization"
                          {:type :forbidden :message "User is not a player in this organization"})))

        ;; Verify voter participated in the pelada (was in a team)
        (let [participation-query "SELECT 1 FROM TeamPlayers tp
                                   JOIN Teams t ON t.id = tp.team_id
                                   WHERE t.pelada_id = ? AND tp.player_id = ?"
              participated (seq (sql/query db [participation-query pelada-id voter-player-id]))]
          (when-not participated
            (throw (ex-info "Player did not participate in this pelada"
                            {:type :forbidden :message "Only players who participated can vote"})))

          ;; Get all players who participated (were in teams) with their names and stats
          (let [query "SELECT op.id as player_id, u.name, u.position,
                              COALESCE(s.goals, 0) as goals, 
                              COALESCE(s.assists, 0) as assists, 
                              COALESCE(s.own_goals, 0) as own_goals
                       FROM OrganizationPlayers op
                       JOIN Users u ON u.id = op.user_id
                       LEFT JOIN PeladaPlayerStats s ON s.player_id = op.id AND s.pelada_id = ?
                       WHERE op.id IN (
                         SELECT player_id FROM TeamPlayers tp
                         JOIN Teams t ON t.id = tp.team_id
                         WHERE t.pelada_id = ?
                       )
                       AND op.id != ?"
                eligible-players-raw (sql/query db [query pelada-id pelada-id voter-player-id])
                eligible-players (mapv (fn [p]
                                         (let [up (misc/unamespace p)]
                                           {:player-id (:player_id up)
                                            :name (:name up)
                                            :position (:position up)
                                            :goals (int (or (:goals up) 0))
                                            :assists (int (or (:assists up) 0))
                                            :own-goals (int (or (:own_goals up) 0))}))
                                       eligible-players-raw)
                has-voted (db.vote/has-voter-voted? pelada-id voter-player-id db)
                current-votes (if has-voted
                                (db.vote/list-votes-by-voter pelada-id voter-player-id db)
                                [])]
            {:can-vote true
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

(s/defn get-voting-results :- responses.vote/VotingResultsResponse
  [pelada-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (when (vote.logic/voting-open? pelada)
      (throw (ex-info "Voting still in progress"
                      {:type :bad-request
                       :message "Results are only available after the voting period ends (24h after close)."})))
    (let [votes (db.vote/list-votes-by-pelada pelada-id db)
        ;; Get all participants (potential voters)
        participants-query "SELECT op.id as player_id, u.name, u.position,
                                   COALESCE(s.goals, 0) as goals, 
                                   COALESCE(s.assists, 0) as assists, 
                                   COALESCE(s.own_goals, 0) as own_goals
                            FROM OrganizationPlayers op
                            JOIN Users u ON u.id = op.user_id
                            LEFT JOIN PeladaPlayerStats s ON s.player_id = op.id AND s.pelada_id = ?
                            WHERE op.id IN (
                              SELECT player_id FROM TeamPlayers tp
                              JOIN Teams t ON t.id = tp.team_id
                              WHERE t.pelada_id = ?
                            )"
        participants-raw (sql/query db [participants-query pelada-id pelada-id])
        participants (mapv (fn [p]
                             (let [up (misc/unamespace p)]
                               {:player-id (:player_id up)
                                :name (:name up)
                                :position (:position up)
                                :goals (int (or (:goals up) 0))
                                :assists (int (or (:assists up) 0))
                                :own-goals (int (or (:own_goals up) 0))}))
                           participants-raw)
        voted-ids (set (map :voter-id votes))
        
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
                 (sort-by :average-stars >)
                 (take 3))
        strikers (->> player-scores
                      (filter #(> (:goals %) 0))
                      (sort-by :goals >)
                      (take 3))
        garcoms (->> player-scores
                     (filter #(> (:assists %) 0))
                     (sort-by :assists >)
                     (take 3))
        
        voter-status (map (fn [p]
                            {:player-id (:player-id p)
                             :name (:name p)
                             :has-voted (voted-ids (:player-id p))})
                          participants)]
    {:mvp mvp
     :striker strikers
     :garcom garcoms
     :voters voter-status
     :total-eligible (count participants)
     :total-voted (count voted-ids)})))
