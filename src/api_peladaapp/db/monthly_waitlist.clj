(ns api-peladaapp.db.monthly-waitlist
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn add-to-waitlist! :- s/Uuid
  [organization-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [query (-> (h/insert-into :MonthlyPlayerWaitlist)
                  (h/values [{:organization_id organization-id
                              :player_id player-id}])
                  (h/returning :id))
        res (jdbc/execute-one! db (hsql/format query) hsql/opts)]
    (:id res)))

(s/defn remove-from-waitlist! :- s/Int
  [organization-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [query (-> (h/delete-from :MonthlyPlayerWaitlist)
                  (h/where [:and
                            [:= :organization_id organization-id]
                            [:= :player_id player-id]]))
        res (jdbc/execute-one! db (hsql/format query) hsql/opts)]
    (hsql/affected-rows-count res)))

(s/defn get-waitlist-entry
  [organization-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [query (-> (h/select :*)
                  (h/from :MonthlyPlayerWaitlist)
                  (h/where [:and
                            [:= :organization_id organization-id]
                            [:= :player_id player-id]]))]
    (jdbc/execute-one! db (hsql/format query) hsql/opts)))

(s/defn list-waitlist-by-org
  [organization-id :- s/Uuid
   db]
  (let [query (-> (h/select :w.id
                            :w.organization_id
                            :w.player_id
                            :w.created_at
                            [:op.user_id :user_id]
                            [:op.member_type :member_type]
                            [[:coalesce :op.position :u.position] :position]
                            [:u.name :user_name]
                            [:u.username :user_username]
                            [:u.avatar_filename :user_avatar_filename])
                  (h/from [:MonthlyPlayerWaitlist :w])
                  (h/join [:OrganizationPlayers :op] [:= :w.player_id :op.id])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/where [:= :w.organization_id organization-id])
                  (h/order-by [:w.created_at :asc]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn promote-player! :- s/Bool
  [organization-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (jdbc/with-transaction [tx db]
    (db.player/update-player player-id {:member-type "mensalista"} tx)
    (remove-from-waitlist! organization-id player-id tx)
    true))
