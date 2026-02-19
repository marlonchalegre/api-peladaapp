(ns api-peladaapp.core
  (:gen-class)
  (:require
   [api-peladaapp.components :as core.components]
   [com.stuartsierra.component :as component]
   [migratus.core :as migratus]))

(defn -main
  [& _args]
  (println "[SYSTEM] Backend process starting...")
  (let [db-name (or (System/getenv "DB_NAME") "peladaapp.db")
        db-spec {:dbtype "sqlite" :dbname db-name}
        skip-migrations (= "true" (System/getenv "SKIP_MIGRATIONS"))
        options {:db-spec db-spec
                 :skip-migrations skip-migrations}
        migratus-config {:store :database
                         :migration-dir "migrations"
                         :db db-spec}]
    (when-not skip-migrations
      (println "[MIGRATION] Starting migration process for" db-name "...")
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