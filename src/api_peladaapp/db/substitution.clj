(ns api-peladaapp.db.substitution
  (:require [next.jdbc.sql :as sql]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-substitution :- s/Int
  [{:keys [match_id minute out_player_id in_player_id]}
   db]
  (-> (sql/insert! (db) :matchsubstitutions {:match_id match_id :minute minute :out_player_id out_player_id :in_player_id in_player_id})
      affected-rows-count))

(s/defn list-substitutions [match-id db]
  (sql/find-by-keys (db) :matchsubstitutions {:match_id match-id}))
