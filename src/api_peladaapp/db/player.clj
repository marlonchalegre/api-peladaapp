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

(s/defn insert-player :- s/Int
  [{:keys [user-id organization-id grade position-id member-type]}
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
  [id player db]
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
  [id grade db]
  (let [query (-> (h/update :OrganizationPlayers)
                  (h/set {:grade grade})
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn delete-player :- s/Int
  [id db]
  (let [query (-> (h/delete-from :OrganizationPlayers)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn get-player [id db]
  (let [query (-> (h/select :*)
                  (h/from :OrganizationPlayers)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        adapter.player/db->model)))

(s/defn get-org-player-by-user-id :- s/Any
  [user-id organization-id db]
  (let [query (-> (h/select :*)
                  (h/from :OrganizationPlayers)
                  (h/where [:= :user_id user-id] [:= :organization_id organization-id]))]
    (some-> (jdbc/execute-one! db (hsql/format query) opts)
            adapter.player/db->model)))

(defn- position-string->id [pos]
  (case (some-> pos str/lower-case)
    "goalkeeper" 1
    "defender" 2
    "midfielder" 3
    "striker" 4
    nil))

(s/defn list-players-by-organization [organization-id db]
  (let [query (-> (h/select :op.* [:u.name :user_name] [:u.username :user_username] [:u.position :user_position] [:u.avatar_filename :avatar_filename])
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/where [:= :op.organization_id organization-id]))]
    (->> (jdbc/execute! db (hsql/format query) opts)
         (map (fn [row]
                (if (nil? (:position_id row))
                  (assoc row :position_id (position-string->id (:user_position row)))
                  row)))
         (map adapter.player/db->model)
         vec)))
