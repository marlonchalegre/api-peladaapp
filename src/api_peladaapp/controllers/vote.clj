(ns api-peladaapp.controllers.vote
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.vote :as db.vote]
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
  {:votes_cast (count votes)})

(s/defn list-votes :- [models.vote/Vote]
  [pelada-id :- s/Int db]
  (db.vote/list-votes-by-pelada pelada-id db))

(s/defn compute-normalized-score :- responses.vote/NormalizedScoreResponse
  "Normalize a player's average stars (1..5) into 1..10 scale."
  [pelada-id :- s/Int player-id :- s/Int db]
  (let [votes (db.vote/list-votes-for-player pelada-id player-id db)
        res (vote.logic/normalized-score player-id votes)]
    {:player_id (:player-id res)
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

        ;; Get all players who participated (were in teams) with their names
        (let [query "SELECT op.id as player_id, u.name
                     FROM OrganizationPlayers op
                     JOIN Users u ON u.id = op.user_id
                     WHERE op.id IN (
                       SELECT player_id FROM TeamPlayers tp
                       JOIN Teams t ON t.id = tp.team_id
                       WHERE t.pelada_id = ?
                     )
                     AND op.id != ?"
              eligible-players (sql/query db [query pelada-id voter-player-id])
              has-voted (db.vote/has-voter-voted? pelada-id voter-player-id db)]
          {:can_vote true
           :has_voted has-voted
           :eligible_players (mapv #(update-keys % (comp keyword name)) eligible-players)
           :voter_player_id voter-player-id}))
      (catch Exception e
        (let [data (ex-data e)]
          {:can_vote false
           :has_voted false
           :eligible_players []
           :message (or (:message data) (.getMessage e))})))))
