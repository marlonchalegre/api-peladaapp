(ns api-peladaapp.db.organization-invitation
  (:require
   [api-peladaapp.adapters.invitation :as adapter.invitation]
   [api-peladaapp.helpers.sql :as hsql]
   [clojure.string :as str]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn insert-invitation :- s/Uuid
  [invitation :- {:organization-id s/Uuid :email (s/maybe s/Str) :token s/Str :invited-by s/Uuid} db]
  (let [row {:organization_id (:organization-id invitation)
             :email (:email invitation)
             :token (:token invitation)
             :invited_by (:invited-by invitation)
             :status [:cast "pending" :invitation_status]}
        query (-> (h/insert-into :OrganizationInvitations)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))

(s/defn get-invitation-by-token
  [token :- s/Str db]
  (let [query (-> (h/select :i.* [:o.name :organization_name])
                  (h/from [:OrganizationInvitations :i])
                  (h/join [:Organizations :o] [:= :i.organization_id :o.id])
                  (h/where [:= :i.token token]))
        result (jdbc/execute! db (hsql/format query) hsql/opts)]
    (some-> result first adapter.invitation/db->model)))

(s/defn list-pending-invitations-by-email
  [email :- s/Str db]
  (let [query (-> (h/select :i.* [:o.name :organization_name])
                  (h/from [:OrganizationInvitations :i])
                  (h/join [:Organizations :o] [:= :i.organization_id :o.id])
                  (h/where [:and [:= [:lower :i.email] (str/lower-case email)] [:= :i.status [:cast "pending" :invitation_status]]]))
        result (jdbc/execute! db (hsql/format query) hsql/opts)]
    (map adapter.invitation/db->model result)))

(s/defn list-pending-invitations-by-identifiers
  [identifiers :- [s/Str] db]
  (let [lower-ids (map str/lower-case identifiers)
        query (-> (h/select :i.* [:o.name :organization_name])
                  (h/from [:OrganizationInvitations :i])
                  (h/join [:Organizations :o] [:= :i.organization_id :o.id])
                  (h/where [:and [:in [:lower :i.email] lower-ids] [:= :i.status [:cast "pending" :invitation_status]]]))
        result (jdbc/execute! db (hsql/format query) hsql/opts)]
    (map adapter.invitation/db->model result)))

(s/defn update-invitation-status :- s/Int
  [id :- s/Uuid status :- s/Str db]
  (let [query (-> (h/update :OrganizationInvitations)
                  (h/set {:status [:cast status :invitation_status]})
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn mark-all-accepted :- s/Int
  [organization-id :- s/Uuid identifiers :- [s/Str] db]
  (let [lower-ids (map str/lower-case identifiers)
        query (-> (h/update :OrganizationInvitations)
                  (h/set {:status [:cast "accepted" :invitation_status]})
                  (h/where [:and [:= :organization_id organization-id] [:= :status [:cast "pending" :invitation_status]] [:in [:lower :email] lower-ids]]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn get-invitation-by-id
  [id :- s/Uuid db]
  (let [query (-> (h/select :*) (h/from :OrganizationInvitations) (h/where [:= :id id]))
        result (jdbc/execute-one! db (hsql/format query) hsql/opts)]
    (some-> result adapter.invitation/db->model)))

(s/defn find-link-invitation-by-org
  [org-id :- s/Uuid db]
  (let [query (-> (h/select :*) (h/from :OrganizationInvitations) (h/where [:and [:= :organization_id org-id] [:is :email nil]]))
        result (jdbc/execute-one! db (hsql/format query) hsql/opts)]
    (some-> result adapter.invitation/db->model)))

(s/defn delete-link-invitation-by-org :- s/Int
  [org-id :- s/Uuid db]
  (let [query (-> (h/delete-from :OrganizationInvitations) (h/where [:and [:= :organization_id org-id] [:is :email nil]]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn list-invitations-by-organization
  [organization-id :- s/Uuid db]
  (let [query (-> (h/select :*) (h/from :OrganizationInvitations) (h/where [:and [:= :organization_id organization-id] [:= :status [:cast "pending" :invitation_status]]]) (h/order-by [:created_at :desc]))
        result (jdbc/execute! db (hsql/format query) hsql/opts)]
    (map adapter.invitation/db->model result)))

(s/defn delete-invitation :- s/Int
  [id :- s/Uuid db]
  (let [query (-> (h/delete-from :OrganizationInvitations) (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))
