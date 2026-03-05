(ns api-peladaapp.logic.authorization
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.player :as db.player]
   [schema.core :as s]))

(s/defn user-can-admin-organization? :- s/Bool
  "Check if user has admin permissions for an organization"
  [user-id organization-id db]
  (db.admin/is-user-admin-of-organization? user-id organization-id db))

(s/defn user-is-in-organization? :- s/Bool
  "Check if user is part of an organization (as player or admin)"
  [user-id organization-id db]
  (or (db.admin/is-user-admin-of-organization? user-id organization-id db)
      (boolean (db.player/get-org-player-by-user-id user-id organization-id db))))

(s/defn require-organization-admin!
  "Throws exception if user is not an admin of the organization"
  [user-id organization-id db]
  (println "[AUTH] Checking admin for user:" user-id "org:" organization-id)
  (when-not (user-can-admin-organization? user-id organization-id db)
    (println "[AUTH] DENIED: user" user-id "is NOT admin of" organization-id)
    (throw (ex-info "User is not an admin of this organization"
                    {:type :forbidden
                     :message "You must be an admin of this organization to perform this action"}))))

(s/defn require-organization-member!
  "Throws exception if user is not a member (player or admin) of the organization"
  [user-id organization-id db]
  (when-not (user-is-in-organization? user-id organization-id db)
    (throw (ex-info "User is not a member of this organization"
                    {:type :forbidden
                     :message "You must be a member of this organization to view this resource"}))))

(defn get-user-id-from-request
  "Extract user ID from request identity"
  [request]
  (get-in request [:identity :id]))

(s/defn require-self-or-admin!
  "Throws exception if user is not the target user and not a global admin"
  [request target-user-id]
  (let [identity (:identity request)
        current-user-id (:id identity)
        is-admin? (:is-admin? identity)]
    (when-not (or (= current-user-id target-user-id) is-admin?)
      (throw (ex-info "Forbidden: You don't have permission to access this resource"
                      {:type :forbidden
                       :message "You don't have permission to access this resource"})))))
