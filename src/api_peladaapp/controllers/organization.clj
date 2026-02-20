(ns api-peladaapp.controllers.organization
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.organization-invitation :as db.invitation]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.models.organization :as models.organization]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(defn- generate-token []
  (str (java.util.UUID/randomUUID)))

(s/defn get-or-create-organization-link :- s/Str
  "Returns the existing public link token or creates a new one"
  [organization-id :- s/Int
   user-id :- s/Int
   db]
  (if-let [existing (db.invitation/find-link-invitation-by-org organization-id db)]
    (:token existing)
    (let [token (generate-token)]
      (db.invitation/insert-invitation {:organization-id organization-id
                                        :token token
                                        :invited-by user-id}
                                       db)
      token)))

(s/defn invite-player
  "Invites a player to an organization. 
   If user doesn't exist, creates a partial user.
   Record personal invitation but DOES NOT add to org yet."
  [organization-id :- s/Int
   email :- s/Str
   invited-by :- (s/maybe s/Int)
   db]
  (let [user (db.user/find-user-by-email email db)
        user-id (if user
                  (:id user)
                  (db.user/insert-partial-user email db))
        existing-invites (db.invitation/list-pending-invitations-by-email email db)]
    ;; Record personal invitation only if not already invited
    (when-not (some #(= (:organization-id %) organization-id) existing-invites)
      (db.invitation/insert-invitation {:organization-id organization-id
                                        :email email
                                        :token (generate-token)
                                        :invited-by invited-by}
                                       db))
    {:user-id user-id
     :email email
     :is-new-user (nil? user)
     :organization-id organization-id}))

(s/defn list-pending-invitations
  [email :- s/Str db]
  (db.invitation/list-pending-invitations-by-email email db))

(s/defn get-invitation-by-token
  [token :- s/Str db]
  (if-let [inv (db.invitation/get-invitation-by-token token db)]
    inv
    (throw (ex-info "Invitation not found" {:type :not-found :message "Invitation not found"}))))

(s/defn accept-invitation
  [token :- s/Str user-id :- s/Int db]
  (let [inv (db.invitation/get-invitation-by-token token db)]
    (if (nil? inv)
      (throw (ex-info "Invitation not found" {:type :not-found}))
      (let [org-id (:organization-id inv)
            is-in-org? (boolean (db.player/get-org-player-by-user-id user-id org-id db))
            user (db.user/find-user-by-id user-id db)]

        ;; Security check: if invitation has email, user email must match
        (when (and (:email inv) (not= (:email inv) (:email user)))
          (throw (ex-info "Invitation does not belong to this user"
                          {:type :forbidden :message "This invitation was sent to another email address."})))

        (jdbc/with-transaction [tx db]
          (when-not is-in-org?
            (jdbc/execute! tx ["INSERT INTO OrganizationPlayers (user_id, organization_id, grade) VALUES (?, ?, 5.0)"
                               user-id org-id]))
          (when (:email inv) ;; Only personal invitations change status
            (db.invitation/update-invitation-status (:id inv) "accepted" tx)))
        {:organization-id org-id}))))

(s/defn list-organization-invitations
  [organization-id :- s/Int db]
  (db.invitation/list-invitations-by-organization organization-id db))

(s/defn revoke-invitation
  [invitation-id :- s/Int organization-id :- s/Int db]
  (let [inv (db.invitation/get-invitation-by-id invitation-id db)]
    (when (and inv (= (:organization-id inv) organization-id))
      (db.invitation/delete-invitation invitation-id db))))

(s/defn create-organization :- models.organization/Organization
  [org :- models.organization/Organization
   user-id :- (s/maybe s/Int)
   db]
  (jdbc/with-transaction [tx db]
    (let [id (db.organization/insert-organization org tx)]
      ;; Add creator as admin and player (if user-id is provided)
      (when user-id
        (db.admin/insert-organization-admin {:organization-id id :user-id user-id} tx)
        (db.player/insert-player {:user-id user-id :organization-id id :grade 5.0} tx))
      (db.organization/get-organization id tx))))

(s/defn get-organization :- models.organization/Organization
  [id :- s/Int
   db]
  (let [org (db.organization/get-organization id db)]
    (if (nil? org)
      (throw (ex-info nil {:type :not-found :message "Organization not found"}))
      org)))

(s/defn update-organization :- models.organization/Organization
  [id :- s/Int
   org :- models.organization/Organization
   db]
  (let [rows (db.organization/update-organization id org db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Organization not found"}))
      (db.organization/get-organization id db))))

(s/defn delete-organization
  [id :- s/Int
   db]
  (let [rows (db.organization/delete-organization id db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Organization not found"}))
      rows)))

(s/defn list-organizations
  [db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (dec page) per-page)
        orgs   (db.organization/list-organizations db per-page offset)
        total  (db.organization/count-organizations db)]
    (pagination/with-pagination-headers orgs total page per-page)))

(s/defn list-user-organizations
  [user-id :- s/Int db]
  (db.organization/list-by-user user-id db))

(s/defn get-statistics
  [id :- s/Int
   year :- s/Int
   db]
  (let [rows (db.organization/get-statistics id year db)]
    (->> rows
         (group-by :player_id)
         (map (fn [[_ player-rows]]
                (let [first-row (first player-rows)
                      base {:player_id (:player_id first-row)
                            :player_name (:player_name first-row)
                            :peladas_played (:peladas_count first-row)
                            :goal 0
                            :assist 0
                            :own_goal 0}]
                  (reduce (fn [acc {:keys [event_type count]}]
                            (if event_type
                              (assoc acc (keyword event_type) count)
                              acc))
                          base
                          player-rows)))))))