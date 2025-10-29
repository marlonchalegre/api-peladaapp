(ns api-peladaapp.db.vote
  (:require [api-peladaapp.adapters.vote :as adapter.vote]
            [next.jdbc.sql :as sql]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-vote :- s/Int
  [{:keys [pelada_id voter_id target_id stars]}
   db]
  (-> (sql/insert! (db) :votes {:pelada_id pelada_id :voter_id voter_id :target_id target_id :stars stars})
      affected-rows-count))

(s/defn list-votes-by-pelada :- [s/Any]
  [pelada-id db]
  (->> (sql/find-by-keys (db) :votes {:pelada_id pelada-id})
       (map adapter.vote/db->model)))

(s/defn list-votes-for-player :- [s/Any]
  [pelada-id player-id db]
  (->> (sql/find-by-keys (db) :votes {:pelada_id pelada-id :target_id player-id})
       (map adapter.vote/db->model)))
