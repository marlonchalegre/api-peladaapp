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
        db-spec (if (and turso-url turso-token)
                  (do
                    (Class/forName "com.dbeaver.jdbc.driver.libsql.LibSqlDriver")
                    {:jdbcUrl (str "jdbc:dbeaver:libsql:https://"
                                   (str/replace turso-url #"^libsql://" ""))
                     :user ""
                     :password turso-token})
                  {:dbtype "sqlite" :dbname db-name})
        skip-migrations (= "true" (System/getenv "SKIP_MIGRATIONS"))
        options {:db-spec db-spec
                 :skip-migrations skip-migrations}
        migratus-config {:store :database
                         :migration-dir "migrations"
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