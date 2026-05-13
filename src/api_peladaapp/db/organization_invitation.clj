(ns api-peladaapp.db.organization-invitation
  (:require
   [api-peladaapp.adapters.invitation :as adapter.invitation]
   [api-peladaapp.helpers.sql :as hsql]
   [clojure.string :as str]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-invitation :- s/Uuid
  [invitation :- {:organization-id s/Uuid :email (s/maybe s/Str) :token s/Str :invited-by s/Uuid} db]
  (let [row {:organization_id (:organization-id invitation)
             :email (:email invitation)
             :token (:token invitation)
             :invited_by (:invited-by invitation)}
        query (-> (h/insert-into :OrganizationInvitations)
                  (h/values [row])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn get-invitation-by-token
  [token :- s/Str db]
  (let [query (-> (h/select :i.* [:o.name :organization_name])
                  (h/from [:OrganizationInvitations :i])
                  (h/join [:Organizations :o] [:= :i.organization_id :o.id])
                  (h/where [:= :i.token token]))
        result (jdbc/execute! db (hsql/format query) opts)]
    (some-> result first adapter.invitation/db->model)))

(s/defn list-pending-invitations-by-email
  [email :- s/Str db]
  (let [query (-> (h/select :i.* [:o.name :organization_name])
                  (h/from [:OrganizationInvitations :i])
                  (h/join [:Organizations :o] [:= :i.organization_id :o.id])
                  (h/where [:and [:= [:lower :i.email] (str/lower-case email)] [:= :i.status "pending"]]))
        result (jdbc/execute! db (hsql/format query) opts)]
    (map adapter.invitation/db->model result)))

(s/defn list-pending-invitations-by-identifiers
  [identifiers :- [s/Str] db]
  (let [lower-ids (map str/lower-case identifiers)
        query (-> (h/select :i.* [:o.name :organization_name])
                  (h/from [:OrganizationInvitations :i])
                  (h/join [:Organizations :o] [:= :i.organization_id :o.id])
                  (h/where [:and [:in [:lower :i.email] lower-ids] [:= :i.status "pending"]]))
        result (jdbc/execute! db (hsql/format query) opts)]
    (map adapter.invitation/db->model result)))

(s/defn update-invitation-status :- s/Int
  [id :- s/Uuid status :- s/Str db]
  (let [query (-> (h/update :OrganizationInvitations)
                  (h/set {:status status})
                  (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn mark-all-accepted :- s/Int
  [organization-id :- s/Uuid identifiers :- [s/Str] db]
  (let [lower-ids (map str/lower-case identifiers)
        query (-> (h/update :OrganizationInvitations)
                  (h/set {:status "accepted"})
                  (h/where [:and [:= :organization_id organization-id] [:= :status "pending"] [:in [:lower :email] lower-ids]]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn get-invitation-by-id
  [id :- s/Uuid db]
  (let [query (-> (h/select :*) (h/from :OrganizationInvitations) (h/where [:= :id id]))
        result (jdbc/execute-one! db (hsql/format query) opts)]
    (some-> result adapter.invitation/db->model)))

(s/defn find-link-invitation-by-org
  [org-id :- s/Uuid db]
  (let [query (-> (h/select :*) (h/from :OrganizationInvitations) (h/where [:and [:= :organization_id org-id] [:is :email nil]]))
        result (jdbc/execute-one! db (hsql/format query) opts)]
    (some-> result adapter.invitation/db->model)))

(s/defn delete-link-invitation-by-org :- s/Int
  [org-id :- s/Uuid db]
  (let [query (-> (h/delete-from :OrganizationInvitations) (h/where [:and [:= :organization_id org-id] [:is :email nil]]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn list-invitations-by-organization
  [organization-id :- s/Uuid db]
  (let [query (-> (h/select :*) (h/from :OrganizationInvitations) (h/where [:and [:= :organization_id organization-id] [:= :status "pending"]]) (h/order-by [:created_at :desc]))
        result (jdbc/execute! db (hsql/format query) opts)]
    (map adapter.invitation/db->model result)))

(s/defn delete-invitation :- s/Int
  [id :- s/Uuid db]
  (let [query (-> (h/delete-from :OrganizationInvitations) (h/where [:= :id id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))
