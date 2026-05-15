(ns api-peladaapp.core
  (:gen-class)
  (:require
   [api-peladaapp.components :as core.components]
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

(defn -main
  [& _args]
  (log/info "[SYSTEM] Backend process starting...")
  (let [;; Build the same spec as in components.clj
        database-url (System/getenv "DATABASE_URL")
        schema (or (System/getenv "DB_SCHEMA") "public")
        ;; Build a db-spec usable by Migratus: prefer Postgres
        base-db-spec (if database-url
                       (let [base-url (if (str/starts-with? database-url "postgres://")
                                        (let [[_ user pass host port db] (re-matches #"^postgres://([^:]+):(.+)@([^@:]+)(?::(\d+))?/(.+)$" database-url)]
                                          (if host
                                            (str "jdbc:postgresql://" host ":" (if port port 5432) "/" db "?user=" user "&password=" pass)
                                            database-url))
                                        database-url)
                             jdbc-url (add-schema-to-url base-url schema)]
                         {:connection-uri jdbc-url})
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
      (let [system (core.components/system options)]
        (log/info "[SYSTEM] Starting system map...")
        (component/start system)
        (log/info "[SYSTEM] All components started and running."))
      (catch Exception e
        (log/error e "[SYSTEM] ERROR during component startup:")
        (System/exit 1)))))
