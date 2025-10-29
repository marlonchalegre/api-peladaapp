(ns api-100folego.controllers.vote
  (:require [api-100folego.db.vote :as db.vote]
            [schema.core :as s]))

(defn- check-self-vote! [voter-id target-id]
  (when (= voter-id target-id)
    (throw (ex-info nil {:type :bad-request :message "Voter cannot vote for himself"}))))

(s/defn cast-vote :- s/Int
  [{:keys [voter_id target_id stars] :as vote} db]
  (check-self-vote! voter_id target_id)
  (when-not (<= 1 stars 5)
    (throw (ex-info nil {:type :bad-request :message "Stars must be 1..5"})))
  (db.vote/insert-vote vote db))

(s/defn list-votes :- [s/Any]
  [pelada-id :- s/Int db]
  (db.vote/list-votes-by-pelada pelada-id db))

(s/defn compute-normalized-score :- {:player_id s/Int :score s/Num}
  "Normalize a player's average stars (1..5) into 1..10 scale."
  [pelada-id :- s/Int player-id :- s/Int db]
  (let [votes (db.vote/list-votes-for-player pelada-id player-id db)
        avg (if (seq votes)
              (/ (reduce + (map :stars votes)) (count votes))
              0.0)
        normalized (* 2.0 avg)]
    {:player_id player-id :score normalized}))
