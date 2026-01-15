(ns api-peladaapp.controllers.vote
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.vote :as vote.logic]
   [api-peladaapp.models.vote :as models.vote]
   [api-peladaapp.responses.vote :as responses.vote]
   [schema.core :as s]))

(s/defn cast-vote :- models.vote/Vote
  [{:keys [_voter_id _target_id _stars pelada_id] :as vote} :- models.vote/Vote db]
  ;; Validate pelada voting eligibility
  (let [pelada (db.pelada/get-pelada pelada_id db)]
    (vote.logic/validate-voting-eligibility pelada))
  ;; Validate individual vote
  (vote.logic/validate-vote vote)
  (let [id (db.vote/insert-vote vote db)]
    (db.vote/get-vote id db)))

(s/defn batch-cast-votes :- responses.vote/BatchVoteResponse
  "Cast multiple votes at once. Replaces any existing votes by this voter."
  [pelada-id :- s/Int voter-id :- s/Int votes :- [{:target_id s/Int :stars s/Int}] db]
  ;; Validate pelada voting eligibility
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (vote.logic/validate-voting-eligibility pelada))
  ;; Delete existing votes by this voter
  (db.vote/delete-votes-by-voter pelada-id voter-id db)
  ;; Insert new votes
  (doseq [vote votes]
    (let [full-vote {:pelada_id pelada-id
                     :voter_id voter-id
                     :target_id (:target_id vote)
                     :stars (:stars vote)}]
      (vote.logic/validate-vote full-vote)
      (db.vote/insert-vote full-vote db)))
  {:votes_cast (count votes)})

(s/defn list-votes :- [models.vote/Vote]
  [pelada-id :- s/Int db]
  (db.vote/list-votes-by-pelada pelada-id db))

(s/defn compute-normalized-score :- responses.vote/NormalizedScoreResponse
  "Normalize a player's average stars (1..5) into 1..10 scale."
  [pelada-id :- s/Int player-id :- s/Int db]
  (let [votes (db.vote/list-votes-for-player pelada-id player-id db)]
    (vote.logic/normalized-score player-id votes)))

(s/defn get-voting-info :- responses.vote/VotingInfoResponse
  "Get voting eligibility info for a voter in a pelada."
  [pelada-id :- s/Int voter-id :- s/Int db]
  (let [pelada (db.pelada/get-pelada pelada-id db)]
    (try
      (vote.logic/validate-voting-eligibility pelada)
      ;; Get all players who participated (were in teams)
      (let [teams (db.team/list-pelada-teams pelada-id db)
            all-player-ids (->> teams
                                (mapcat #(db.team/list-team-players (:id %) db))
                                (map :player_id)
                                distinct
                                (remove #(= % voter-id))) ;; exclude self
            has-voted (db.vote/has-voter-voted? pelada-id voter-id db)]
        {:can_vote true
         :has_voted has-voted
         :eligible_players (vec all-player-ids)})
      (catch Exception e
        (let [data (ex-data e)]
          {:can_vote false
           :has_voted false
           :eligible_players []
           :message (or (:message data) (.getMessage e))})))))