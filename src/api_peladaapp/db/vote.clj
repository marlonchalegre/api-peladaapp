(ns api-peladaapp.db.vote
  (:require
   [api-peladaapp.adapters.vote :as adapter.vote]
   [api-peladaapp.logic.grade :as logic.grade]
   [api-peladaapp.models.vote :as models.vote]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn get-vote :- (s/maybe models.vote/Vote)
  [id :- s/Int db]
  (-> (sql/get-by-id db :votes id)
      adapter.vote/db->model))

(s/defn insert-vote :- s/Int
  [{:keys [pelada-id voter-id target-id stars]}
   db]
  (let [row {:pelada_id pelada-id :voter_id voter-id :target_id target-id :stars stars}]
    (-> (sql/insert! db :votes row)
        affected-rows-count
        int)))

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

(s/defn delete-votes-for-target :- s/Int
  "Delete all votes cast for a target player in a pelada."
  [pelada-id target-id db]
  (-> (sql/delete! db :votes {:pelada_id pelada-id :target_id target-id})
      affected-rows-count))

(s/defn insert-votes-batch :- s/Int
  "Insert multiple votes at once."
  [votes-data db]
  (if (empty? votes-data)
    0
    (let [query "INSERT INTO votes (pelada_id, voter_id, target_id, stars) VALUES (?, ?, ?, ?)"
          params (map (fn [v]
                        [(:pelada-id v)
                         (:voter-id v)
                         (:target-id v)
                         (:stars v)])
                      votes-data)]
      (doseq [p params]
        (jdbc/execute! db (into [query] p)))
      (count votes-data))))

(s/defn list-ranking-by-pelada
  [pelada-id db]
  (let [query "SELECT v.target_id, u.id as user_id, u.name as player_name, u.avatar_filename, AVG(v.stars) as avg_stars, COUNT(v.id) as vote_count
               FROM Votes v
               JOIN OrganizationPlayers op ON v.target_id = op.id
               JOIN Users u ON op.user_id = u.id
               JOIN PeladaAttendance pa ON pa.player_id = op.id AND pa.pelada_id = v.pelada_id
               WHERE v.pelada_id = ? AND COALESCE(pa.voting_enabled, 1) = 1
               GROUP BY v.target_id
               ORDER BY avg_stars DESC, vote_count DESC"
        results (jdbc/execute! db [query pelada-id] {:builder-fn rs/as-unqualified-lower-maps})]
    (map (fn [r]
           {:player-id (:target_id r)
            :user-id (:user_id r)
            :player-name (:player_name r)
            :avatar-filename (:avatar_filename r)
            :avg-stars (:avg_stars r)
            :score (logic.grade/performance-from-stars (:avg_stars r))
            :vote-count (:vote_count r)})
         results)))

(s/defn list-pending-voters-by-pelada [pelada-id db]
  (let [query "SELECT pa.player_id, u.name as player_name
               FROM PeladaAttendance pa
               JOIN OrganizationPlayers op ON pa.player_id = op.id
               JOIN Users u ON op.user_id = u.id
               WHERE pa.pelada_id = ? AND pa.status = 'confirmed'
               AND NOT EXISTS (
                 SELECT 1 FROM Votes v WHERE v.pelada_id = pa.pelada_id AND v.voter_id = pa.player_id
               )"
        results (jdbc/execute! db [query pelada-id] {:builder-fn rs/as-unqualified-lower-maps})]
    (map (fn [r] {:player-id (:player_id r) :player-name (:player_name r)}) results)))
