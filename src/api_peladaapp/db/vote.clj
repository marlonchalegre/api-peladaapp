(ns api-peladaapp.db.vote
  (:require
   [api-peladaapp.adapters.vote :as adapter.vote]
   [api-peladaapp.models.vote :as models.vote]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn get-vote :- (s/maybe models.vote/Vote)
  [id :- s/Int db]
  (-> (sql/get-by-id db :votes id)
      adapter.vote/db->model))

(s/defn insert-vote :- s/Int
  [{:keys [pelada_id voter_id target_id stars]}
   db]
  (sql/insert! db :votes {:pelada_id pelada_id :voter_id voter_id :target_id target_id :stars stars})
  (-> (jdbc/execute-one! db ["select last_insert_rowid() as id"]) :id int))

(s/defn list-votes-by-pelada :- [models.vote/Vote]
  [pelada-id db]
  (->> (sql/find-by-keys db :votes {:pelada_id pelada-id})
       (map adapter.vote/db->model)))

(s/defn list-votes-for-player :- [models.vote/Vote]
  [pelada-id player-id db]
  (->> (sql/find-by-keys db :votes {:pelada_id pelada-id :target_id player-id})
       (map adapter.vote/db->model)))

(s/defn list-votes-by-voter :- [models.vote/Vote]
  "Get all votes cast by a specific voter in a pelada."
  [pelada-id voter-id db]
  (->> (sql/find-by-keys db :votes {:pelada_id pelada-id :voter_id voter-id})
       (map adapter.vote/db->model)))

(s/defn has-voter-voted? :- s/Bool
  "Check if a voter has cast any votes in a pelada."
  [pelada-id voter-id db]
  (-> (sql/find-by-keys db :votes {:pelada_id pelada-id :voter_id voter-id})
      seq
      boolean))

(s/defn delete-votes-by-voter :- s/Int
  "Delete all votes by a voter in a pelada (for re-voting)."
  [pelada-id voter-id db]
  (-> (sql/delete! db :votes {:pelada_id pelada-id :voter_id voter-id})
      affected-rows-count))