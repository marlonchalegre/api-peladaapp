(ns api-100folego.controllers.substitution
  (:require [api-100folego.db.match :as db.match]
            [api-100folego.db.substitution :as db.substitution]
            [api-100folego.db.team :as db.team]
            [schema.core :as s]))

(defn- pelada-player-ids [pelada-id db]
  (->> (db.team/list-pelada-teams pelada-id db)
       (map :id)
       (map #(db.team/list-team-players % db))
       (mapcat identity)
       (map :player_id)
       set))

(s/defn create-substitution :- s/Int
  [{:keys [match_id out_player_id in_player_id minute] :as sub} db]
  (when (= out_player_id in_player_id)
    (throw (ex-info nil {:type :bad-request :message "in and out cannot be the same"})))
  (let [match (db.match/get-match match_id db)]
    (when (nil? match)
      (throw (ex-info nil {:type :not-found :message "Match not found"})))
    (let [allowed (pelada-player-ids (:pelada_id match) db)]
      (when (or (not (contains? allowed out_player_id))
                (not (contains? allowed in_player_id)))
        (throw (ex-info nil {:type :bad-request :message "Players must belong to pelada teams"})))
      (db.substitution/insert-substitution sub db))))

(s/defn list-substitutions [match-id db]
  (db.substitution/list-substitutions match-id db))
