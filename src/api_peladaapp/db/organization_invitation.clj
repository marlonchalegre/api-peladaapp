(ns api-peladaapp.db.organization-invitation
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(def opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-invitation :- s/Int
  [invitation db]
  (let [row {:organization_id (:organization-id invitation)
             :email (:email invitation)
             :token (:token invitation)
             :invited_by (:invited-by invitation)}]
    (-> (sql/insert! db :organizationinvitations row)
        affected-rows-count)))

(s/defn get-invitation-by-token
  [token db]
  (some-> (jdbc/execute! db ["SELECT i.*, o.name as organization_name 
                              FROM OrganizationInvitations i
                              JOIN Organizations o ON i.organization_id = o.id
                              WHERE i.token = ?" token] opts)
          first))

(s/defn list-pending-invitations-by-email
  [email db]
  (jdbc/execute! db ["SELECT i.*, o.name as organization_name 
                      FROM OrganizationInvitations i
                      JOIN Organizations o ON i.organization_id = o.id
                      WHERE i.email = ? AND i.status = 'pending'" email] opts))

(s/defn update-invitation-status :- s/Int
  [id status db]
  (-> (sql/update! db :organizationinvitations {:status status} {:id id})
      affected-rows-count))

(s/defn get-invitation-by-id
  [id db]
  (some-> (jdbc/execute! db ["SELECT * FROM OrganizationInvitations WHERE id = ?" id] opts)
          first))

(s/defn find-link-invitation-by-org
  [org-id db]
  (some-> (jdbc/execute! db ["SELECT * FROM OrganizationInvitations WHERE organization_id = ? AND email IS NULL" org-id] opts)
          first))

(s/defn list-invitations-by-organization
  [organization-id db]
  (jdbc/execute! db ["SELECT * FROM OrganizationInvitations WHERE organization_id = ? ORDER BY created_at DESC" organization-id] opts))

(s/defn delete-invitation :- s/Int
  [id db]
  (-> (sql/delete! db :organizationinvitations {:id id})
      affected-rows-count))
