(ns api-100folego.controllers.substitution
  (:require [api-100folego.db.match :as db.match]
            [api-100folego.db.substitution :as db.substitution]
            [api-100folego.db.team :as db.team]
            [api-100folego.logic.substitution :as substitution.logic]
            [schema.core :as s]))

(defn- pelada-player-ids [pelada-id db]
  (->> (db.team/list-pelada-teams pelada-id db)
       (map :id)
       (map #(db.team/list-team-players % db))
       (mapcat identity)
       (map :player_id)
       set))

(s/defn create-substitution :- s/Int
  [{:keys [match_id out_player_id in_player_id] :as sub} db]
  (let [match (db.match/get-match match_id db)]
    (when (nil? match)
      (throw (ex-info nil {:type :not-found :message "Match not found"})))
    (let [allowed (pelada-player-ids (:pelada_id match) db)]
      (substitution.logic/validate-substitution sub allowed)
      (db.substitution/insert-substitution sub db))))

(s/defn list-substitutions [match-id db]
  (db.substitution/list-substitutions match-id db))
