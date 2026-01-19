(ns api-peladaapp.core
  (:gen-class)
  (:require
   [api-peladaapp.components :as core.components]
   [com.stuartsierra.component :as component]))

(defn -main
  []
  (let [options {:db-spec {:dbname (or (System/getenv "DB_NAME") "peladaapp.db")}
                 :skip-migrations (= "true" (System/getenv "SKIP_MIGRATIONS"))}]
    (component/start (core.components/system options))))