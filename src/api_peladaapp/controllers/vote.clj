(ns api-peladaapp.controllers.vote
  (:require [api-peladaapp.db.vote :as db.vote]
            [api-peladaapp.db.pelada :as db.pelada]
            [api-peladaapp.db.team :as db.team]
            [api-peladaapp.logic.vote :as vote.logic]
            [schema.core :as s]))

(s/defn cast-vote :- s/Int
  [{:keys [voter_id target_id stars pelada_id] :as vote} db]
  ;; Validate pelada voting eligibility
  (let [pelada (db.pelada/get-pelada pelada_id db)]
    (vote.logic/validate-voting-eligibility pelada))
  ;; Validate individual vote
  (vote.logic/validate-vote vote)
  (db.vote/insert-vote vote db))

(s/defn batch-cast-votes :- {:votes-cast s/Int}
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
  {:votes-cast (count votes)})

(s/defn list-votes :- [s/Any]
  [pelada-id :- s/Int db]
  (db.vote/list-votes-by-pelada pelada-id db))

(s/defn compute-normalized-score :- {:player_id s/Int :score s/Num}
  "Normalize a player's average stars (1..5) into 1..10 scale."
  [pelada-id :- s/Int player-id :- s/Int db]
  (let [votes (db.vote/list-votes-for-player pelada-id player-id db)]
    (vote.logic/normalized-score player-id votes)))

(s/defn get-voting-info :- {:can_vote s/Bool
                             :has_voted s/Bool
                             :eligible_players [s/Int]
                             (s/optional-key :message) s/Str}
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
