(ns api-100folego.db.team
  (:require [api-100folego.adapters.team :as adapter.team]
            [next.jdbc.sql :as sql]
            [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn insert-team :- s/Int
  [{:keys [pelada_id name]}
   db]
  (-> (sql/insert! (db) :teams {:pelada_id pelada_id :name name})
      affected-rows-count))

(s/defn get-team [id db]
  (-> (sql/get-by-id (db) :teams id)
      adapter.team/db->model))

(s/defn update-team :- s/Int
  [id team db]
  (-> (sql/update! (db) :teams (select-keys team [:name]) {:id id})
      affected-rows-count))

(s/defn delete-team :- s/Int
  [id db]
  (-> (sql/delete! (db) :teams {:id id}) affected-rows-count))

(s/defn list-pelada-teams [pelada-id db]
  (->> (sql/find-by-keys (db) :teams {:pelada_id pelada-id})
       (map adapter.team/db->model)))

(s/defn add-player-to-team :- s/Int
  [team-id player-id db]
  (-> (sql/insert! (db) :teamplayers {:team_id team-id :player_id player-id})
      affected-rows-count))

(s/defn remove-player-from-team :- s/Int
  [team-id player-id db]
  (-> (sql/delete! (db) :teamplayers {:team_id team-id :player_id player-id})
      affected-rows-count))

(s/defn list-team-players [team-id db]
  (->> (sql/find-by-keys (db) :teamplayers {:team_id team-id})
       (map (fn [m] (update-keys m (comp keyword name))))))
