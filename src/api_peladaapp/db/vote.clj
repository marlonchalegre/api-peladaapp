(ns api-peladaapp.db.vote
  (:require
   [api-peladaapp.adapters.vote :as adapter.vote]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.grade :as logic.grade]
   [api-peladaapp.models.vote :as models.vote]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn get-vote :- (s/maybe models.vote/Vote)
  [id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :Votes)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        adapter.vote/db->model)))

(s/defn insert-vote :- s/Uuid
  [{:keys [pelada-id voter-id target-id stars]} :- {:pelada-id s/Uuid :voter-id s/Uuid :target-id s/Uuid :stars s/Int}
   db]
  (let [row {:pelada_id pelada-id :voter_id voter-id :target_id target-id :stars stars}
        query (-> (h/insert-into :Votes)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn list-votes-by-pelada :- [models.vote/Vote]
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :Votes)
                  (h/where [:= :pelada_id pelada-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.vote/db->model))))

(s/defn list-votes-for-player :- [models.vote/Vote]
  [pelada-id :- s/Uuid player-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :Votes)
                  (h/where [:= :pelada_id pelada-id] [:= :target_id player-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.vote/db->model))))

(s/defn list-votes-by-voter :- [models.vote/Vote]
  "Get all votes cast by a specific voter in a pelada."
  [pelada-id :- s/Uuid voter-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :Votes)
                  (h/where [:= :pelada_id pelada-id] [:= :voter_id voter-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.vote/db->model))))

(s/defn has-voter-voted? :- s/Bool
  "Check if a voter has cast any votes in a pelada."
  [pelada-id :- s/Uuid voter-id :- s/Uuid db]
  (let [query (-> (h/select [[:count :*] :count])
                  (h/from :Votes)
                  (h/where [:and [:= :pelada_id pelada-id] [:= :voter_id voter-id]]))
        res (jdbc/execute-one! db (hsql/format query) opts)]
    (> (int (or (:count res) (first (vals res)) 0)) 0)))

(s/defn delete-votes-by-voter :- s/Int
  "Delete all Votes by a voter in a pelada (for re-voting)."
  [pelada-id :- s/Uuid voter-id :- s/Uuid db]
  (let [query (-> (h/delete-from :Votes)
                  (h/where [:and [:= :pelada_id pelada-id] [:= :voter_id voter-id]]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn delete-votes-for-target :- s/Int
  "Delete all Votes cast for a target player in a pelada."
  [pelada-id :- s/Uuid target-id :- s/Uuid db]
  (let [query (-> (h/delete-from :Votes)
                  (h/where [:and [:= :pelada_id pelada-id] [:= :target_id target-id]]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn insert-votes-batch :- s/Int
  "Insert multiple Votes at once."
  [votes-data db]
  (if (empty? votes-data)
    0
    (let [rows (map (fn [v]
                      {:pelada_id (:pelada-id v)
                       :voter_id (:voter-id v)
                       :target_id (:target-id v)
                       :stars (:stars v)})
                    votes-data)
          query (-> (h/insert-into :Votes)
                    (h/values rows))]
      (jdbc/execute! db (hsql/format query))
      (count rows))))

(s/defn list-ranking-by-pelada
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :v.target_id [:u.id :user_id] [:u.name :player_name] :u.avatar_filename
                            [[:avg :v.stars] :avg_stars] [[:count :v.id] :vote_count])
                  (h/from [:Votes :v])
                  (h/join [:OrganizationPlayers :op] [:= :v.target_id :op.id])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/join [:Attendance :pa] [:and [:= :pa.player_id :op.id] [:= :pa.pelada_id :v.pelada_id]])
                  (h/where [:and [:= :v.pelada_id pelada-id] [:= [[:coalesce :pa.voting_enabled true]] true]])
                  (h/group-by :v.target_id :u.id :u.name :u.avatar_filename)
                  (h/order-by [:avg_stars :desc] [:vote_count :desc]))
        results (jdbc/execute! db (hsql/format query) opts)]
    (map (fn [r]
           {:player-id (:target_id r)
            :user-id (:user_id r)
            :player-name (:player_name r)
            :avatar-filename (:avatar_filename r)
            :avg-stars (double (or (:avg_stars r) 0.0))
            :score (logic.grade/performance-from-stars (double (or (:avg_stars r) 0.0)))
            :vote-count (int (or (:vote_count r) 0))})
         results)))

(s/defn list-pending-voters-by-pelada [pelada-id :- s/Uuid db]
  (let [query (-> (h/select [:pa.player_id :player_id] [:u.name :player_name] :u.phone)
                  (h/from [:Attendance :pa])
                  (h/join [:OrganizationPlayers :op] [:= :pa.player_id :op.id])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/where [:and
                            [:= :pa.pelada_id pelada-id]
                            [:= :pa.status "confirmed"]
                            [:not-exists (-> (h/select 1)
                                             (h/from :Votes :v)
                                             (h/where [:= :v.pelada_id :pa.pelada_id] [:= :v.voter_id :pa.player_id]))]]))
        results (jdbc/execute! db (hsql/format query) opts)]
    (map (fn [r] {:player-id (:player_id r) :player-name (:player_name r) :phone (:phone r)}) results)))

(s/defn list-eligible-players-for-voting
  [pelada-id :- s/Uuid voter-player-id :- (s/maybe s/Uuid) db]
  (let [where-clause (cond-> [:and [:in :op.id (-> (h/select :player_id)
                                                   (h/from [:TeamPlayers :sub_tp])
                                                   (h/join [:Teams :sub_t] [:= :sub_t.id :sub_tp.team_id])
                                                   (h/where [:= :sub_t.pelada_id pelada-id]))]]
                       voter-player-id (conj [:!= :op.id voter-player-id]))
        query (-> (h/select [:op.id :player_id] [:u.id :user_id] :u.name :u.position :u.avatar_filename
                            [[:coalesce :pa.voting_enabled true] :voting_enabled]
                            [[:coalesce :s.goals 0] :goals]
                            [[:coalesce :s.assists 0] :assists]
                            [[:coalesce :s.own_goals 0] :own_goals])
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :u.id :op.user_id])
                  (h/left-join [:Attendance :pa] [:and [:= :pa.player_id :op.id] [:= :pa.pelada_id pelada-id]])
                  (h/left-join [:PeladaPlayerStats :s] [:and [:= :s.player_id :op.id] [:= :s.pelada_id pelada-id]])
                  (h/where where-clause))]
    (jdbc/execute! db (hsql/format query) opts)))

(s/defn list-pelada-participants
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select [:op.id :player_id] [:u.id :user_id] :u.name :u.position :u.avatar_filename
                            [[:coalesce :s.goals 0] :goals]
                            [[:coalesce :s.assists 0] :assists]
                            [[:coalesce :s.own_goals 0] :own_goals])
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :u.id :op.user_id])
                  (h/left-join [:PeladaPlayerStats :s] [:and [:= :s.player_id :op.id] [:= :s.pelada_id pelada-id]])
                  (h/where [:in :op.id (-> (h/select :player_id)
                                           (h/from [:TeamPlayers :sub_tp])
                                           (h/join [:Teams :sub_t] [:= :sub_t.id :sub_tp.team_id])
                                           (h/where [:= :sub_t.pelada_id pelada-id]))]))]
    (jdbc/execute! db (hsql/format query) opts)))
