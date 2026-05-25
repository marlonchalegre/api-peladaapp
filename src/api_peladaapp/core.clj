(ns api-peladaapp.core
  (:gen-class)
  (:require
   [api-peladaapp.components :as core.components]
   [buddy.hashers :as hashers]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [migratus.core :as migratus]
   [next.jdbc]))

(defn- add-schema-to-url [jdbc-url schema]
  (if (or (str/blank? schema) (= "public" schema))
    jdbc-url
    (let [separator (if (str/includes? jdbc-url "?") "&" "?")]
      (if (str/includes? jdbc-url "currentSchema=")
        jdbc-url ;; Already has it
        (str jdbc-url separator "currentSchema=" schema)))))

(defn- ensure-super-admin [db]
  (when-let [admin-email (System/getenv "SUPER_ADMIN_EMAIL")]
    (when-not (str/blank? admin-email)
      (log/info (str "[BOOTSTRAP] Ensuring super admin status for email: " admin-email))
      (try
        (let [existing-user (next.jdbc/execute-one! db ["SELECT id FROM \"Users\" WHERE LOWER(email) = LOWER(?)" admin-email])]
          (if (nil? existing-user)
            (if-let [admin-password (System/getenv "SUPER_ADMIN_PASSWORD")]
              (if-not (str/blank? admin-password)
                (let [hashed-password (hashers/encrypt admin-password)
                      username (or (first (str/split admin-email #"@")) "superadmin")
                      id (java.util.UUID/randomUUID)
                      insert-query ["INSERT INTO \"Users\" (id, name, username, email, password, is_super_admin, allow_org_creation) VALUES (?, ?, ?, ?, ?, TRUE, TRUE)"
                                    id "Super Admin" username admin-email hashed-password]]
                  (next.jdbc/execute! db insert-query)
                  (log/info "[BOOTSTRAP] Created super admin user successfully."))
                (log/warn "[BOOTSTRAP] SUPER_ADMIN_EMAIL set but SUPER_ADMIN_PASSWORD is empty. Skipping super admin creation."))
              (log/warn "[BOOTSTRAP] SUPER_ADMIN_EMAIL set but SUPER_ADMIN_PASSWORD is not provided. Skipping super admin creation."))
            ;; If exists, update flags to ensure they are super admin
            (let [update-query ["UPDATE \"Users\" SET is_super_admin = TRUE, allow_org_creation = TRUE WHERE LOWER(email) = LOWER(?)" admin-email]
                  result (next.jdbc/execute! db update-query)]
              (log/info (str "[BOOTSTRAP] Ensured existing super admin flags: " result)))))
        (catch Exception e
          (log/error e "[BOOTSTRAP] Failed to ensure super admin status: " (.getMessage e)))))))

(defn -main
  [& _args]
  (log/info "[SYSTEM] Backend process starting...")
  (let [;; Build the same spec as in components.clj
        database-url (System/getenv "DATABASE_URL")
        schema (or (System/getenv "DB_SCHEMA") "public")
        ;; Build a db-spec usable by Migratus: prefer Postgres
        base-db-spec (if database-url
                       (if (str/starts-with? database-url "postgres://")
                         (let [[_ user pass host port db] (re-matches #"^postgres://([^:]+):(.+)@([^@:]+)(?::(\d+))?/(.+)$" database-url)]
                           (if host
                             {:dbtype "postgresql"
                              :dbname db
                              :host host
                              :port (if port (Integer/parseInt port) 5432)
                              :user user
                              :password pass
                              :currentSchema schema}
                             (throw (Exception. "Invalid DATABASE_URL format"))))
                         {:connection-uri (add-schema-to-url database-url schema)})
                       (throw (Exception. "DATABASE_URL is required for migrations. PostgreSQL is now mandatory.")))

        skip-migrations (= "true" (System/getenv "SKIP_MIGRATIONS"))
        options {:db-spec base-db-spec
                 :skip-migrations skip-migrations}

        migratus-config {:store :database
                         :migration-dir "migrations"
                         :db base-db-spec}]
    (if (not skip-migrations)
      (do
        (log/info (str "[MIGRATION] Starting migration process for (schema: " schema ") ..."))
        (try
          ;; Ensure the schema exists before migrating if it's not public
          (when (not= "public" schema)
            (let [ds (next.jdbc/get-datasource base-db-spec)]
              (next.jdbc/execute! ds [(str "CREATE SCHEMA IF NOT EXISTS " schema)])))

          (migratus/migrate (assoc migratus-config :schema schema))
          (log/info "[MIGRATION] Finished migration process.")
          (catch Exception e
            (log/error e "[MIGRATION] ERROR during migration:")
            (System/exit 1))))
      (log/info "[MIGRATION] Migrations skipped via environment variable."))

    (log/info "[SYSTEM] Initializing components...")
    (try
      (let [system (core.components/system options)
            started-system (component/start system)
            db-val (get-in started-system [:database :database])]
        (ensure-super-admin db-val)
        (log/info "[SYSTEM] All components started and running."))
      (catch Exception e
        (log/error e "[SYSTEM] ERROR during component startup:")
        (System/exit 1)))))
