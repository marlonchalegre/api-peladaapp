(ns api-100folego.controllers.vote
  (:require [api-100folego.db.vote :as db.vote]
            [api-100folego.logic.vote :as vote.logic]
            [schema.core :as s]))

(s/defn cast-vote :- s/Int
  [{:keys [voter_id target_id stars] :as vote} db]
  (vote.logic/validate-vote vote)
  (db.vote/insert-vote vote db))

(s/defn list-votes :- [s/Any]
  [pelada-id :- s/Int db]
  (db.vote/list-votes-by-pelada pelada-id db))

(s/defn compute-normalized-score :- {:player_id s/Int :score s/Num}
  "Normalize a player's average stars (1..5) into 1..10 scale."
  [pelada-id :- s/Int player-id :- s/Int db]
  (let [votes (db.vote/list-votes-for-player pelada-id player-id db)]
    (vote.logic/normalized-score player-id votes)))
