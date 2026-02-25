(ns api-peladaapp.db.organization-invitation
  (:require
   [api-peladaapp.adapters.invitation :as adapter.invitation]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (-> result vals first))

(def opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-invitation :- s/Int
  [invitation db]
  (let [row [(:organization-id invitation)
             (:email invitation)
             (:token invitation)
             (:invited-by invitation)]
        result (jdbc/execute! db ["INSERT INTO OrganizationInvitations (organization_id, email, token, invited_by) VALUES (?, ?, ?, ?)"
                                  (nth row 0) (nth row 1) (nth row 2) (nth row 3)])]
    (affected-rows-count (first result))))

(s/defn get-invitation-by-token
  [token db]
  (some-> (jdbc/execute! db ["SELECT i.*, o.name as organization_name 
                              FROM OrganizationInvitations i
                              JOIN Organizations o ON i.organization_id = o.id
                              WHERE i.token = ?" token] opts)
          first
          adapter.invitation/db->model))

(s/defn list-pending-invitations-by-email
  [email db]
  (->> (jdbc/execute! db ["SELECT i.*, o.name as organization_name 
                      FROM OrganizationInvitations i
                      JOIN Organizations o ON i.organization_id = o.id
                      WHERE LOWER(i.email) = LOWER(?) AND i.status = 'pending'" email] opts)
       (map adapter.invitation/db->model)))

(s/defn list-pending-invitations-by-identifiers
  [identifiers db]
  (let [placeholders (clojure.string/join "," (repeat (count identifiers) "LOWER(?)"))
        sql (str "SELECT i.*, o.name as organization_name 
                  FROM OrganizationInvitations i
                  JOIN Organizations o ON i.organization_id = o.id
                  WHERE LOWER(i.email) IN (" placeholders ") AND i.status = 'pending'")]
    (->> (jdbc/execute! db (into [sql] identifiers) opts)
         (map adapter.invitation/db->model))))

(s/defn update-invitation-status :- s/Int
  [id status db]
  (-> (sql/update! db :organizationinvitations {:status status} {:id id})
      affected-rows-count))

(s/defn get-invitation-by-id
  [id db]
  (some-> (jdbc/execute! db ["SELECT * FROM OrganizationInvitations WHERE id = ?" id] opts)
          first
          adapter.invitation/db->model))

(s/defn find-link-invitation-by-org
  [org-id db]
  (some-> (jdbc/execute! db ["SELECT * FROM OrganizationInvitations WHERE organization_id = ? AND email IS NULL" org-id] opts)
          first
          adapter.invitation/db->model))

(s/defn list-invitations-by-organization
  [organization-id db]
  (->> (jdbc/execute! db ["SELECT * FROM OrganizationInvitations WHERE organization_id = ? ORDER BY created_at DESC" organization-id] opts)
       (map adapter.invitation/db->model)))

(s/defn delete-invitation :- s/Int
  [id db]
  (let [result (jdbc/execute! db ["DELETE FROM OrganizationInvitations WHERE id = ?" id])]
    (affected-rows-count (first result))))
