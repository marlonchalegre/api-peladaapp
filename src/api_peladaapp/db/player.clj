(ns api-peladaapp.db.player
  (:require
   [api-peladaapp.adapters.player :as adapter.player]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [medley.core :as medley.core]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn insert-player :- s/Uuid
  [{:keys [user-id organization-id grade position member-type passing ball-control velocity shooting dribbling defending]} :- {:user-id s/Uuid
                                                                                                                               :organization-id s/Uuid
                                                                                                                               (s/optional-key :grade) (s/maybe s/Num)
                                                                                                                               (s/optional-key :position) (s/maybe s/Str)
                                                                                                                               (s/optional-key :member-type) (s/maybe s/Str)
                                                                                                                               (s/optional-key :passing) (s/maybe s/Int)
                                                                                                                               (s/optional-key :ball-control) (s/maybe s/Int)
                                                                                                                               (s/optional-key :velocity) (s/maybe s/Int)
                                                                                                                               (s/optional-key :shooting) (s/maybe s/Int)
                                                                                                                               (s/optional-key :dribbling) (s/maybe s/Int)
                                                                                                                               (s/optional-key :defending) (s/maybe s/Int)}
   db]
  (let [row (cond-> {:user_id user-id
                     :organization_id organization-id
                     :grade grade
                     :member_type [:cast (or member-type "convidado") :member_type]}
              position (assoc :position [:cast position :player_position])
              passing (assoc :passing passing)
              ball-control (assoc :ball_control ball-control)
              velocity (assoc :velocity velocity)
              shooting (assoc :shooting shooting)
              dribbling (assoc :dribbling dribbling)
              defending (assoc :defending defending))
        query (-> (h/insert-into :OrganizationPlayers)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))

(s/defn update-player :- s/Int
  [id :- s/Uuid player db]
  (let [row (medley.core/assoc-some {}
                                    :grade (:grade player)
                                    :position (when (:position player) [:cast (:position player) :player_position])
                                    :member_type (when (:member-type player) [:cast (:member-type player) :member_type])
                                    :passing (:passing player)
                                    :ball_control (:ball-control player)
                                    :velocity (:velocity player)
                                    :shooting (:shooting player)
                                    :dribbling (:dribbling player)
                                    :defending (:defending player))
        query (-> (h/update :OrganizationPlayers)
                  (h/set row)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn update-player-grade :- s/Int
  "Surgically update a player's grade."
  [id :- s/Uuid grade db]
  (let [query (-> (h/update :OrganizationPlayers)
                  (h/set {:grade grade})
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn delete-player :- s/Int
  [id :- s/Uuid db]
  (let [query (-> (h/delete-from :OrganizationPlayers)
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn get-player
  ([id :- s/Uuid db] (get-player id db false))
  ([id :- s/Uuid db for-update?]
   (let [query (cond-> (-> (h/select :op.* [:u.position :user_position])
                           (h/from [:OrganizationPlayers :op])
                           (h/join [:Users :u] [:= :op.user_id :u.id])
                           (h/where [:= :op.id id]))
                 for-update? (assoc :for [:update]))]
     (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
         adapter.player/db->model))))

(s/defn get-org-player-by-user-id :- s/Any
  [user-id :- s/Uuid organization-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :OrganizationPlayers)
                  (h/where [:= :user_id [:cast user-id :uuid]] [:= :organization_id [:cast organization-id :uuid]]))]
    (some-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
            adapter.player/db->model)))

(s/defn list-players-by-organization [organization-id :- s/Uuid db]
  (let [query (-> (h/select :op.* [:u.name :user_name] [:u.username :user_username] [[:coalesce :op.position :u.position] :user_position] [:u.avatar_filename :avatar_filename])
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/where [:= :op.organization_id organization-id]))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
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
      (jdbc/execute! db (hsql/format query) hsql/opts))))

(s/defn get-players-details-for-balance :- [s/Any]
  [player-ids :- [s/Uuid]
   organization-id :- s/Uuid
   db]
  (if (empty? player-ids)
    []
    (let [query (-> (h/select [:op.id :id] [:op.grade :grade] [[:coalesce :op.position :u.position] :position])
                    (h/from [:OrganizationPlayers :op])
                    (h/join [:Users :u] [:= :op.user_id :u.id])
                    (h/where [:= :op.organization_id organization-id]
                             [:in :op.id player-ids]))]
      (jdbc/execute! db (hsql/format query) hsql/opts))))
