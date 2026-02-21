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
                    {:jdbcUrl (str "jdbc:libsql://" 
                                   (str/replace turso-url #"^libsql://" "") 
                                   "?authToken=" turso-token)})
                  {:dbtype "sqlite" :dbname db-name})
        skip-migrations (= "true" (System/getenv "SKIP_MIGRATIONS"))
        options {:db-spec db-spec
                 :skip-migrations skip-migrations}
        migratus-config {:store :database
                         :migration-dir "migrations"
                         :db db-spec}]
    (when-not skip-migrations
      (println (str "[MIGRATION] Starting migration process for " (or (:jdbcUrl db-spec) (:dbname db-spec)) " ..."))
      (try
        (migratus/migrate migratus-config)
        (println "[MIGRATION] Finished migration process.")
        (catch Exception e
          (println "[MIGRATION] ERROR during migration:")
          (.printStackTrace e)
          (System/exit 1))))

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