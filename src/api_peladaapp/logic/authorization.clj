(ns api-peladaapp.logic.authorization
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.user :as db.user]
   [schema.core :as s]))

(s/defn user-can-admin-organization? :- s/Bool
  "Check if user has admin permissions for an organization"
  [user-id organization-id db]
  (let [user (try
               (db.user/find-user-by-id user-id db)
               (catch Throwable _ nil))]
    (or (true? (:is-global-admin user))
        (db.admin/is-user-admin-of-organization? user-id organization-id db))))

(s/defn user-is-in-organization? :- s/Bool
  "Check if user is part of an organization (as player or admin)"
  [user-id organization-id db]
  (let [admin? (user-can-admin-organization? user-id organization-id db)
        player? (boolean (db.player/get-org-player-by-user-id user-id organization-id db))]
    (or admin? player?)))

(s/defn require-organization-admin!
  "Throws exception if user is not an admin of the organization"
  [user-id organization-id db]
  (when-not (user-can-admin-organization? user-id organization-id db)
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
        is-global-admin? (:is-global-admin? identity)]
    (when-not (or (= current-user-id target-user-id) is-global-admin?)
      (throw (ex-info "Forbidden: You don't have permission to access this resource"
                      {:type :forbidden
                       :message "You don't have permission to access this resource"})))))

(def ^:private friendly-feature-names
  {:finance_control "Controle Financeiro"
   :waha_communications "Notificações do WhatsApp (WAHA)"
   :player_characteristics "Ficha de Atributos do Jogador"
   :monthly_substitutions "Mensalistas Substitutos"
   :org_statistics "Estatísticas & Analytics"
   :peer_voting "Votação Pós-Jogo"
   :unlimited_members "Membros Ilimitados"
   :unlimited_peladas "Peladas Ilimitadas"})

(s/defn require-feature-flag!
  "Throws exception if the specified feature flag is not enabled for the organization."
  [organization-id :- s/Uuid
   feature-key :- s/Keyword
   db]
  (let [flags (try (db.organization/get-organization-feature-flags organization-id db) (catch Throwable _ nil))
        enabled? (if (nil? flags) true (true? (get flags feature-key)))]
    (when-not enabled?
      (let [friendly-name (get friendly-feature-names feature-key (name feature-key))]
        (throw (ex-info (str "Feature " (name feature-key) " is not enabled for this organization")
                        {:type :forbidden
                         :message (str "Esta funcionalidade premium (" friendly-name ") não está ativa para esta organização.")}))))))

(s/defn check-member-limit!
  "Checks if the organization has reached its player limit (15) and throws forbidden if it is a free organization."
  [organization-id :- s/Uuid
   db]
  (let [flags (try (db.organization/get-organization-feature-flags organization-id db) (catch Throwable _ nil))
        unlimited? (if (nil? flags) true (true? (:unlimited_members flags)))]
    (when-not unlimited?
      (let [count (try (db.player/count-players-by-org organization-id db) (catch Throwable _ 0))]
        (when (>= count 15)
          (throw (ex-info "Limite de membros atingido"
                          {:type :forbidden
                           :message "Esta organização atingiu o limite máximo de 15 membros para a versão gratuita. Por favor, faça o upgrade para Premium para adicionar mais membros."})))))))

(s/defn check-pelada-limit!
  "Checks if the organization has reached its peladas limit (2 per month) and throws forbidden if it is a free organization."
  [organization-id :- s/Uuid
   db]
  (let [flags (try (db.organization/get-organization-feature-flags organization-id db) (catch Throwable _ nil))
        unlimited? (if (nil? flags) true (true? (:unlimited_peladas flags)))]
    (when-not unlimited?
      (let [now (java.time.LocalDate/now)
            year (.getYear now)
            month (.getMonthValue now)
            count (try (db.pelada/count-peladas-in-month-by-org organization-id year month db) (catch Throwable _ 0))]
        (when (>= count 2)
          (throw (ex-info "Limite de peladas atingido"
                          {:type :forbidden
                           :message "Esta organização atingiu o limite máximo de 2 peladas criadas por mês para a versão gratuita. Por favor, faça o upgrade para Premium."})))))))


