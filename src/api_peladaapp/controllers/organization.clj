(ns api-peladaapp.controllers.organization
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.match-event :as db.match-event]
   [api-peladaapp.db.match-lineup :as db.match-lineup]
   [api-peladaapp.db.monthly-substitution :as db.monthly-sub]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.organization-invitation :as db.invitation]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.helpers.pagination :as pagination]
   [api-peladaapp.logic.notifications :as notifications]
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
                  (not (str/blank? email)) (db.user/insert-partial-user {:email email} db)
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

        (when-not is-in-org?
          (let [flags (db.organization/get-organization-feature-flags org-id db)
                unlimited? (if (nil? flags) true (true? (:unlimited_members flags)))]
            (when-not unlimited?
              (let [count (db.player/count-players-by-org org-id db)]
                (when (>= count 15)
                  (throw (ex-info "Limite de membros atingido"
                                  {:type :forbidden
                                   :message "Esta organização atingiu o limite máximo de 15 membros para a versão gratuita. Por favor, peça ao administrador para fazer o upgrade para Premium."})))))))

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
      (db.organization/insert-default-feature-flags id tx)
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
      (throw (ex-info "Organization not found" {:type :not-found :message "Organization not found"}))
      org)))

(s/defn update-organization :- models.organization/Organization
  [id :- s/Uuid
   org :- models.organization/Organization
   db]
  (let [rows (db.organization/update-organization id org db)]
    (if (zero? rows)
      (throw (ex-info "Organization not found" {:type :not-found :message "Organization not found"}))
      (db.organization/get-organization id db))))

(s/defn test-waha-connection
  [id :- s/Uuid db]
  (let [org (db.organization/get-organization id db)]
    (if (and org (:waha-enabled org))
      (let [result (waha/send-message org "PeladaApp: Teste de conexão WAHA realizado com sucesso! ⚽")]
        (if (:error result)
          (throw (ex-info (str "Erro no WAHA: " (:error result)) {:type :bad-request :message (str "Erro no WAHA: " (:error result))}))
          {:status "success" :message "Mensagem de teste enviada!"}))
      (throw (ex-info "WAHA não está habilitado para esta organização." {:type :bad-request :message "WAHA não está habilitado para esta organização."})))))

(s/defn delete-organization
  [id :- s/Uuid
   db]
  (let [rows (db.organization/delete-organization id db)]
    (if (zero? rows)
      (throw (ex-info "Organization not found" {:type :not-found :message "Organization not found"}))
      rows)))

(s/defn list-organizations
  [db pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (dec page) per-page)
        orgs   (db.organization/list-organizations db per-page offset)
        total  (db.organization/count-organizations db)]
    (pagination/with-pagination-headers orgs total page per-page)))

(s/defn search-organizations
  [db query pagination]
  (let [page (or (:page pagination) 1)
        per-page (or (:per-page pagination) 20)
        offset (* (dec page) per-page)
        orgs   (db.organization/search-organizations db query per-page offset)
        total  (db.organization/count-searched-organizations db query)]
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

(s/defn leave-organization
  [org-id :- s/Uuid
   user-id :- s/Uuid
   db]
  (let [is-admin? (db.admin/is-user-admin-of-organization? user-id org-id db)
        admin-count (db.admin/count-admins-by-organization org-id db)
        player (db.player/get-org-player-by-user-id user-id org-id db)]
    (when (and is-admin? (<= admin-count 1))
      (throw (ex-info "Cannot leave organization: you are the last administrator."
                      {:type :bad-request
                       :message "Cannot leave organization: you are the last administrator."})))
    (jdbc/with-transaction [tx db]
      (when is-admin?
        (db.admin/delete-organization-admin-by-org-and-user org-id user-id tx))
      (when player
        (db.player/delete-player (:id player) tx)))))

(s/defn list-monthly-substitutions
  [org-id :- s/Uuid db]
  (db.monthly-sub/list-substitutions-by-org org-id db))

(s/defn send-custom-message
  [org-id :- s/Uuid message :- s/Str db]
  (let [org (db.organization/get-organization org-id db)]
    (if (and org (:waha-enabled org))
      (let [result (waha/send-message org message nil)]
        (if (:error result)
          (throw (ex-info (str "Erro no WAHA: " (:error result)) {:type :bad-request :message (str "Erro no WAHA: " (:error result))}))
          {:status "success" :message "Mensagem personalizada enviada!"}))
      (throw (ex-info "WAHA não está habilitado para esta organização." {:type :bad-request :message "WAHA não está habilitado para esta organização."})))))

(s/defn resend-notification
  [org-id :- s/Uuid notification-type-str :- s/Str pelada-id :- s/Uuid db]
  (let [org (db.organization/get-organization org-id db)
        pelada (db.pelada/get-pelada pelada-id db)]
    (when-not org
      (throw (ex-info "Organização não encontrada" {:type :not-found :message "Organização não encontrada"})))
    (when-not pelada
      (throw (ex-info "Pelada não encontrada" {:type :not-found :message "Pelada não encontrada"})))
    (when-not (= (:organization-id pelada) org-id)
      (throw (ex-info "Pelada não pertence a esta organização" {:type :bad-request :message "Pelada não pertence a esta organização"})))
    (if (and org (:waha-enabled org))
      (let [notification-type (keyword notification-type-str)]
        (case notification-type
          :new-pelada
          (let [confirmed-players (db.attendance/list-confirmed-players-by-pelada pelada-id db)]
            (notifications/send-notification! org-id :new-pelada {:pelada-id pelada-id :scheduled-at (:scheduled-at pelada) :confirmed-players confirmed-players :force? true} db))

          :start
          (let [teams (db.team/list-pelada-teams pelada-id db)
                team-players (db.team/list-team-players-with-names-by-pelada pelada-id db)]
            (notifications/send-notification! org-id :start {:teams teams :team-players team-players :force? true} db))

          :end
          (let [matches (db.match/list-matches-by-pelada pelada-id db)
                teams (db.team/list-pelada-teams pelada-id db)
                events (db.match-event/list-events-by-pelada pelada-id db)
                lineups (db.match-lineup/list-match-lineups-by-pelada pelada-id db)
                team-players (db.team/list-team-players-with-names-by-pelada pelada-id db)
                org-players (db.player/list-players-by-organization org-id db)
                all-players (distinct (concat team-players (map (fn [p] {:player_id (:id p) :player_name (:user-name p)}) org-players)))]
            (notifications/send-notification! org-id :end
                                              {:pelada pelada
                                               :matches matches
                                               :teams teams
                                               :events events
                                               :lineups lineups
                                               :team-players all-players
                                               :force? true}
                                              db))

          :vote-ended
          (let [ranking (db.vote/list-ranking-by-pelada pelada-id db)]
            (notifications/send-notification! org-id :vote-ended {:ranking ranking :pelada-id pelada-id :force? true} db))

          :attendance-reminder
          (let [pending (db.attendance/list-pending-mensalistas-by-pelada pelada-id db)]
            (notifications/send-notification! org-id :attendance-reminder {:pending-players pending :pelada-id pelada-id :force? true} db))

          :vote-reminder
          (let [pending (db.vote/list-pending-voters-by-pelada pelada-id db)]
            (notifications/send-notification! org-id :vote-reminder {:pending-voters pending :pelada-id pelada-id :force? true} db))

          ;; default case
          (throw (ex-info (str "Tipo de notificação inválido: " notification-type-str)
                          {:type :bad-request :message (str "Tipo de notificação inválido: " notification-type-str)})))
        {:status "success" :message "Notificação reenviada com sucesso!"})
      (throw (ex-info "WAHA não está habilitado para esta organização." {:type :bad-request :message "WAHA não está habilitado para esta organização."})))))