(ns api-peladaapp.controllers.organization
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.organization-invitation :as db.invitation]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.logic.waha :as waha]
   [api-peladaapp.models.organization :as models.organization]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(defn- generate-token []
  (str (java.util.UUID/randomUUID)))

(s/defn get-or-create-organization-link :- s/Str
  "Returns the existing public link token or creates a new one"
  [organization-id :- s/Uuid
   user-id :- s/Uuid
   db]
  (if-let [existing (db.invitation/find-link-invitation-by-org organization-id db)]
    (:token existing)
    (let [token (generate-token)]
      (db.invitation/insert-invitation {:organization-id organization-id
                                        :token token
                                        :invited-by user-id}
                                       db)
      token)))

(s/defn reset-organization-link :- s/Str
  "Deletes any existing public link and creates a new one"
  [organization-id :- s/Uuid
   user-id :- s/Uuid
   db]
  (jdbc/with-transaction [tx db]
    (db.invitation/delete-link-invitation-by-org organization-id tx)
    (let [token (generate-token)]
      (db.invitation/insert-invitation {:organization-id organization-id
                                        :token token
                                        :invited-by user-id}
                                       tx)
      token)))

(s/defn invite-player-improved
  "Invites a player by email (handle) or name.
   If handle is provided, uses/creates user by handle.
   If only name is provided, creates a user with just a name.
   Returns invitation info including token for automation.
   Prevents duplicate pending invitations."
  [organization-id :- s/Uuid
   email :- (s/maybe s/Str)
   name :- (s/maybe s/Str)
   invited-by :- (s/maybe s/Uuid)
   db]
  (let [user (when-not (str/blank? email) (db.user/find-user-by-identifier email db))
        user-id (cond
                  user (:id user)
                  (not (str/blank? email)) (db.user/insert-partial-user email db)
                  (not (str/blank? name)) (db.user/insert-user-by-name name db)
                  :else (throw (ex-info "Email or Name required" {:type :bad-request})))
        identifier (or email (str "guest-" user-id))]

    (if-let [existing (first (db.invitation/list-pending-invitations-by-identifiers [identifier] db))]
      (let [player (db.player/get-org-player-by-user-id user-id organization-id db)]
        {:user-id user-id
         :player-id (:id player)
         :email email
         :name name
         :token (:token existing)
         :is-new-user (nil? user)
         :organization-id organization-id})

      (let [token (generate-token)]
        (db.invitation/insert-invitation {:organization-id organization-id
                                          :email identifier
                                          :token token
                                          :invited-by invited-by}
                                         db)
        (let [player (db.player/get-org-player-by-user-id user-id organization-id db)]
          {:user-id user-id
           :player-id (:id player)
           :email email
           :name name
           :token token
           :is-new-user (nil? user)
           :organization-id organization-id})))))

(s/defn invite-player
  "Legacy invite-player wrapper"
  [organization-id :- s/Uuid
   email :- s/Str
   invited-by :- (s/maybe s/Uuid)
   db]
  (invite-player-improved organization-id email nil invited-by db))

(s/defn list-pending-invitations
  [email :- s/Str db]
  (db.invitation/list-pending-invitations-by-email email db))

(s/defn list-pending-invitations-for-user
  [email username db]
  (let [identifiers (remove clojure.string/blank? [email username])]
    (if (empty? identifiers)
      []
      (db.invitation/list-pending-invitations-by-identifiers identifiers db))))

(s/defn get-invitation-by-token
  [token :- s/Str db]
  (if-let [inv (db.invitation/get-invitation-by-token token db)]
    inv
    (throw (ex-info "Invitation not found" {:type :not-found :message "Invitation not found"}))))

(s/defn accept-invitation
  [token :- s/Str user-id :- s/Uuid db]
  (let [inv (db.invitation/get-invitation-by-token token db)]
    (if (nil? inv)
      (throw (ex-info "Invitation not found" {:type :not-found}))
      (let [org-id (:organization-id inv)
            is-in-org? (boolean (db.player/get-org-player-by-user-id user-id org-id db))
            user (db.user/find-user-by-id user-id db)]

        ;; Security check: if invitation has email, user email or username must match
        (let [identifier (:email inv)]
          (when (and identifier
                     (if (str/starts-with? identifier "guest-")
                       ;; For guest invitations, ensure the current user ID matches the guest user ID
                       (let [guest-user-id (try (parse-uuid (subs identifier 6)) (catch Exception _ nil))]
                         (not= guest-user-id user-id))
                       ;; For email invitations, ensure email or username matches
                       (and (not= identifier (:email user))
                            (not= identifier (:username user)))))
            (throw (ex-info "Invitation does not belong to this user"
                            {:type :forbidden :message "This invitation was sent to another identifier."}))))

        (jdbc/with-transaction [tx db]
          (when-not is-in-org?
            (db.player/insert-player {:user-id user-id :organization-id org-id :grade 5.0 :member-type "convidado"} tx))

          ;; Clean up all pending invitations for this user in this organization
          (let [identifiers (remove str/blank? [(:email user) (:username user)])]
            (db.invitation/mark-all-accepted org-id identifiers tx)))

        {:organization-id org-id}))))

(s/defn list-organization-invitations
  [organization-id :- s/Uuid db]
  (db.invitation/list-invitations-by-organization organization-id db))

(s/defn revoke-invitation
  [invitation-id :- s/Uuid organization-id :- s/Uuid db]
  (let [inv (db.invitation/get-invitation-by-id invitation-id db)]
    (when (and inv (= (:organization-id inv) organization-id))
      (db.invitation/delete-invitation invitation-id db))))

(s/defn create-organization :- models.organization/Organization
  [org :- models.organization/Organization
   user-id :- (s/maybe s/Uuid)
   db]
  (jdbc/with-transaction [tx db]
    (let [org-with-owner (if user-id (assoc org :owner-id user-id) org)
          id (db.organization/insert-organization org-with-owner tx)]
      ;; Add creator as admin and player (if user-id is provided)
      (when user-id
        (db.admin/insert-organization-admin {:organization-id id :user-id user-id} tx)
        (db.player/insert-player {:user-id user-id :organization-id id :grade 5.0 :member-type "mensalista"} tx))
      (db.organization/get-organization id tx))))

(s/defn get-organization :- models.organization/Organization
  [id :- s/Uuid
   db]
  (let [org (db.organization/get-organization id db)]
    (if (nil? org)
      (throw (ex-info nil {:type :not-found :message "Organization not found"}))
      org)))

(s/defn update-organization :- models.organization/Organization
  [id :- s/Uuid
   org :- models.organization/Organization
   db]
  (let [rows (db.organization/update-organization id org db)]
    (if (zero? rows)
      (throw (ex-info nil {:type :not-found :message "Organization not found"}))
      (db.organization/get-organization id db))))

(s/defn test-waha-connection
  [id :- s/Uuid db]
  (let [org (db.organization/get-organization id db)]
    (if (and org (:waha-enabled org))
      (let [result (waha/send-message org "PeladaApp: Teste de conexão WAHA realizado com sucesso! ⚽")]
        (if (:error result)
          (throw (ex-info "WAHA error" {:type :bad-request :message (str "Erro no WAHA: " (:error result))}))
          {:status "success" :message "Mensagem de teste enviada!"}))
      (throw (ex-info "WAHA not enabled" {:type :bad-request :message "WAHA não está habilitado para esta organização."})))))

(s/defn delete-organization
  [id :- s/Uuid
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
  [user-id :- s/Uuid db]
  (db.organization/list-by-user user-id db))

(s/defn get-statistics
  [id :- s/Uuid
   year :- s/Int
   db]
  (let [rows (db.organization/get-statistics id year db)]
    (->> rows
         (group-by :player_id)
         (map (fn [[_ player-rows]]
                (let [first-row (first player-rows)
                      base {:player_id (:player_id first-row)
                            :user_id (:user_id first-row)
                            :player_name (:player_name first-row)
                            :player_position (:player_position first-row)
                            :avatar_filename (:avatar_filename first-row)
                            :peladas_played (:peladas_count first-row)
                            :avg_rating (:avg_rating first-row)
                            :goal 0
                            :assist 0
                            :own_goal 0}]
                  (reduce (fn [acc {:keys [event_type count]}]
                            (if event_type
                              (assoc acc (keyword event_type) count)
                              acc))
                          base
                          player-rows)))))))