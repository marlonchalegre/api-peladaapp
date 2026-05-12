(ns api-peladaapp.core
  (:gen-class)
  (:require
   [api-peladaapp.components :as core.components]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [migratus.core :as migratus]))

(defn -main
  [& _args]
  (println "[SYSTEM] Backend process starting...")
  (let [turso-url (System/getenv "TURSO_DATABASE_URL")
        turso-token (System/getenv "TURSO_AUTH_TOKEN")
        db-name (or (System/getenv "DB_NAME") "peladaapp.db")
        ;; Build the same spec as in components.clj
        database-url (System/getenv "DATABASE_URL")
        ;; Build a db-spec usable by Migratus: prefer Turso, then Postgres (parsed), else sqlite
        db-spec (cond
                  (and turso-url turso-token)
                  (do
                    (Class/forName "com.dbeaver.jdbc.driver.libsql.LibSqlDriver")
                    {:jdbcUrl (str "jdbc:dbeaver:libsql://" (str/replace turso-url #"^libsql://" ""))
                     :user ""
                     :password turso-token})

                  database-url
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

                  :else
                  {:dbtype "sqlite" :dbname db-name})
        skip-migrations (= "true" (System/getenv "SKIP_MIGRATIONS"))
        options {:db-spec db-spec
                 :skip-migrations skip-migrations}
        ;; Choose migration directory based on DB type (sqlite vs postgres)
        migrations-dir (let [db-url (System/getenv "DATABASE_URL")]
                         (if (or db-url (and (string? (:jdbcUrl db-spec)) (str/starts-with? (:jdbcUrl db-spec) "jdbc:postgresql:")))
                           "migrations-postgres"
                           "migrations"))
        migratus-config {:store :database
                         :migration-dir migrations-dir
                         :db db-spec}
        ;; Check if we are using Turso
        is-turso (and turso-url turso-token)]
    (if (and (not skip-migrations) (not is-turso))
      (do
        (println (str "[MIGRATION] Starting migration process for " (:dbname db-spec) " ..."))
        (try
          (migratus/migrate migratus-config)
          (println "[MIGRATION] Finished migration process.")
          (catch Exception e
            (println "[MIGRATION] ERROR during migration:")
            (.printStackTrace e)
            (System/exit 1))))
      (if is-turso
        (println "[MIGRATION] Skipping automated Migratus migrations for Turso (Cloud DB). Please run migrations manually or via CI.")
        (println "[MIGRATION] Migrations skipped via environment variable.")))

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