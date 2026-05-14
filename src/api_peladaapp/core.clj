(ns api-peladaapp.core
  (:gen-class)
  (:require
   [api-peladaapp.components :as core.components]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [migratus.core :as migratus]
   [next.jdbc]))

(defn -main
  [& _args]
  (println "[SYSTEM] Backend process starting...")
  (let [;; Build the same spec as in components.clj
        database-url (System/getenv "DATABASE_URL")
        schema (or (System/getenv "DB_SCHEMA") "public")
        ;; Build a db-spec usable by Migratus: prefer Postgres
        db-spec (if database-url
                  (let [uri (try (java.net.URI. database-url) (catch Exception _ nil))
                        user-info (when uri (.getUserInfo uri))
                        [user pass] (when user-info (clojure.string/split user-info #":" 2))
                        host (when uri (.getHost uri))
                        port (when uri (.getPort uri))
                        path (when uri (.getPath uri))
                        db (when path (let [p (if (.startsWith path "/") (subs path 1) path)] p))]
                    ;; Provide a clojure.java.jdbc style map for migratus
                    {:dbtype "postgresql"
                     :dbname (or db "peladaapp")
                     :host host
                     :port (when (and port (pos? port)) port)
                     :user user
                     :password pass})
                  (throw (Exception. "DATABASE_URL is required for migrations. PostgreSQL is now mandatory.")))

        skip-migrations (= "true" (System/getenv "SKIP_MIGRATIONS"))
        options {:db-spec db-spec
                 :skip-migrations skip-migrations}

        migratus-config {:store :database
                         :migration-dir "migrations"
                         :db db-spec}]
    (if (not skip-migrations)
      (do
        (println (str "[MIGRATION] Starting migration process for " (:dbname db-spec) " (schema: " schema ") ..."))
        (try
          ;; Ensure the schema exists before migrating if it's not public
          (when (not= "public" schema)
            (let [ds (next.jdbc/get-datasource (assoc db-spec :dbtype "postgresql"))]
              (next.jdbc/execute! ds [(str "CREATE SCHEMA IF NOT EXISTS " schema)])))

          (migratus/migrate (assoc migratus-config :schema schema))
          (println "[MIGRATION] Finished migration process.")
          (catch Exception e
            (println "[MIGRATION] ERROR during migration:")
            (.printStackTrace e)
            (System/exit 1))))
      (println "[MIGRATION] Migrations skipped via environment variable."))

    (println "[SYSTEM] Initializing components...")
    (try
      (let [system (core.components/system options)]
        (println "[SYSTEM] Starting system map...")
        (component/start system)
        (println "[SYSTEM] All components started and running."))
      (catch Exception e
        (println "[SYSTEM] ERROR during component startup:")
        (.printStackTrace e)
        (System/exit 1)))))
