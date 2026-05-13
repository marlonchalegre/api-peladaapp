(ns api-peladaapp.db.player
  (:require
   [api-peladaapp.adapters.player :as adapter.player]
   [api-peladaapp.helpers.sql :as hsql]
   [clojure.string :as str]
   [honey.sql.helpers :as h]
   [medley.core :as medley.core]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-player :- s/Uuid
  [{:keys [user-id organization-id grade position-id member-type]} :- {:user-id s/Uuid
                                                                       :organization-id s/Uuid
                                                                       (s/optional-key :grade) (s/maybe s/Num)
                                                                       (s/optional-key :position-id) (s/maybe s/Uuid)
                                                                       (s/optional-key :member-type) (s/maybe s/Str)}
   db]
  (let [row (medley.core/assoc-some
             {:user_id user-id
              :organization_id organization-id
              :grade grade
              :member_type (or member-type "convidado")}
             :position_id position-id)
        query (-> (h/insert-into :OrganizationPlayers)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn update-player :- s/Int
  [id :- s/Uuid player db]
  (let [row (medley.core/assoc-some {}
                                    :grade (:grade player)
                                    :position_id (:position-id player)
                                    :member_type (:member-type player))
        query (-> (h/update :OrganizationPlayers)
                  (h/set row)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn update-player-grade :- s/Int
  "Surgically update a player's grade."
  [id :- s/Uuid grade db]
  (let [query (-> (h/update :OrganizationPlayers)
                  (h/set {:grade grade})
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn delete-player :- s/Int
  [id :- s/Uuid db]
  (let [query (-> (h/delete-from :OrganizationPlayers)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn get-player
  ([id :- s/Uuid db] (get-player id db false))
  ([id :- s/Uuid db for-update?]
   (let [query (cond-> (-> (h/select :*)
                           (h/from :OrganizationPlayers)
                           (h/where [:= :id id]))
                 for-update? (assoc :for [:update]))]
     (-> (jdbc/execute-one! db (hsql/format query) opts)
         adapter.player/db->model))))

(s/defn get-org-player-by-user-id :- s/Any
  [user-id :- s/Uuid organization-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :OrganizationPlayers)
                  (h/where [:= :user_id [:cast user-id :uuid]] [:= :organization_id [:cast organization-id :uuid]]))]
    (some-> (jdbc/execute-one! db (hsql/format query) opts)
            adapter.player/db->model)))

(s/defn list-players-by-organization [organization-id :- s/Uuid db]
  (let [query (-> (h/select :op.* [:u.name :user_name] [:u.username :user_username] [:u.position :user_position] [:u.avatar_filename :avatar_filename])
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/where [:= :op.organization_id organization-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map adapter.player/db->model)
         vec)))

(s/defn get-players-grades :- [{:id s/Uuid :grade s/Num}]
  [player-ids :- [s/Uuid]
   db]
  (if (empty? player-ids)
    []
    (let [query (-> (h/select :id :grade)
                    (h/from :OrganizationPlayers)
                    (h/where [:in :id player-ids]))]
      (jdbc/execute! db (hsql/format query) opts))))

(s/defn get-players-details-for-balance :- [s/Any]
  [player-ids :- [s/Uuid]
   organization-id :- s/Uuid
   db]
  (if (empty? player-ids)
    []
    (let [query (-> (h/select [:op.id :id] [:op.grade :grade] [:u.position :position])
                    (h/from [:OrganizationPlayers :op])
                    (h/join [:Users :u] [:= :op.user_id :u.id])
                    (h/where [:= :op.organization_id organization-id]
                             [:in :op.id player-ids]))]
      (jdbc/execute! db (hsql/format query) opts))))
