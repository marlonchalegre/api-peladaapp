(ns api-100folego.components
  (:require
   [api-100folego.server :as server]
   [com.stuartsierra.component :as component]
   [migratus.core :as migratus]
   [next.jdbc.connection :as connection]
   [ring.adapter.jetty :refer [run-jetty]])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(def ^:private db-spec {:dbtype "sqlite" :dbname "100folego.db"})

(defrecord Database [database connection]
  component/Lifecycle
  (start [component]
    (let [ds (component/start (connection/component HikariDataSource db-spec))]
      ;; Run DB migrations on startup (idempotent)
      (migratus/migrate {:store :database
                         ;; Use classpath resource dir name for jar compatibility
                         :migration-dir "migrations"
                         :db {:dbtype "sqlite" :dbname "100folego.db"}})
      (assoc component :database ds)))
  (stop [component]
    (-> component :database component/stop)
    component))

(defn new-database []
  (map->Database {}))

(defrecord WebServer [port app]
  component/Lifecycle

  (start [component]
    (assoc component
           ::jetty
           (run-jetty (-> component :app :handler)
                      {:port 8080
                       :join? false})))

  (stop [component]
    (-> component ::jetty .stop)
    component))

(defn new-web-server []
  (component/using
   (map->WebServer {})
   [:app]))

(defn wrap-assoc [f key value]
  (fn [request] (f (assoc request key value))))

(defrecord App [database handler]
  component/Lifecycle

  (start [component]
    (let [database (-> component :database :database)
          ;; The rest of the codebase expects `:database` in the request to be a
          ;; zero-arg function returning a javax.sql.DataSource. In tests we
          ;; inject such a function; do the same in the running app.
          database-fn (fn [] database)]
      (assoc component :handler
             (wrap-assoc handler :database database-fn))))
  (stop [component]
        component))

(defn new-app [handler]
  (component/using
   (map->App {:handler handler})
   [:database]))

(defn system [_]
  (component/system-map
   :database (new-database)
   :app      (new-app server/app)
   :server   (new-web-server)))


