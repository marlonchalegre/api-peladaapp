(ns api-peladaapp.db.match
  (:require [api-peladaapp.adapters.match :as adapter.match]
            [next.jdbc.sql :as sql]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-match :- s/Int
  [{:keys [pelada_id home_team_id away_team_id sequence status home_score away_score]}
   db]
  (-> (sql/insert! (db) :matches {:pelada_id pelada_id
                                  :home_team_id home_team_id
                                  :away_team_id away_team_id
                                  :sequence sequence
                                  :status status
                                  :home_score home_score
                                  :away_score away_score})
      affected-rows-count))

(s/defn list-matches-by-pelada :- [s/Any]
  [pelada-id db]
  (->> (sql/find-by-keys (db) :matches {:pelada_id pelada-id})
       (sort-by :matches/sequence)
       (map adapter.match/db->model)))

(s/defn get-match [id db]
  (-> (sql/get-by-id (db) :matches id)
      adapter.match/db->model))

(s/defn update-score :- s/Int
  [id {:keys [home_score away_score status]} db]
  (-> (sql/update! (db) :matches (cond-> {}
                                    (some? home_score) (assoc :home_score home_score)
                                    (some? away_score) (assoc :away_score away_score)
                                    status (assoc :status status))
                   {:id id})
      affected-rows-count))

(s/defn update-sequence :- s/Int
  [id sequence db]
  (-> (sql/update! (db) :matches {:sequence sequence} {:id id})
      affected-rows-count))

(s/defn finish-all-by-pelada :- s/Int
  [pelada-id :- s/Int db]
  (-> (sql/update! (db) :matches {:status "finished"} {:pelada_id pelada-id})
      affected-rows-count))
