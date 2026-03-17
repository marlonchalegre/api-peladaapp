(ns api-peladaapp.components
  (:require
   [api-peladaapp.components.scheduler :as scheduler]
   [api-peladaapp.server :as server]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [ring.adapter.jetty :refer [run-jetty]])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(defrecord Database [database connection db-spec skip-migrations]
  component/Lifecycle
  (start [this]
    (println "Starting Database component...")
    (let [turso-url (System/getenv "TURSO_DATABASE_URL")
          turso-token (System/getenv "TURSO_AUTH_TOKEN")
          final-db-spec (if (and turso-url turso-token)
                          (let [url (str "jdbc:dbeaver:libsql:https://"
                                         (clojure.string/replace turso-url #"^libsql://" ""))]
                            (println (str "Using Turso (LibSQL) Cloud Database: " url))
                            ;; Force load the driver class for DriverManager
                            (Class/forName "com.dbeaver.jdbc.driver.libsql.LibSqlDriver")
                            {:jdbcUrl url
                             :user ""
                             :password turso-token
                             :connectionTestQuery "SELECT 1"})
                          (do
                            (println "Using local SQLite database")
                            (merge {:dbtype "sqlite" :dbname "peladaapp.db"} db-spec)))
          ds-component (connection/component HikariDataSource final-db-spec)
          started-ds (component/start ds-component)]
      ;; Enable WAL mode for local SQLite
      (when-not (and turso-url turso-token)
        (try
          (with-open [conn (jdbc/get-connection (:connectable started-ds))]
            (jdbc/execute! conn ["PRAGMA journal_mode=WAL;"]))
          (catch Exception e
            (println "Warning: Could not enable WAL mode:" (.getMessage e)))))
      (assoc this :database started-ds)))
  (stop [this]
    (println "Stopping Database component...")
    (when-let [ds (:database this)]
      (component/stop ds))
    (assoc this :database nil)))

(defn new-database [db-spec skip-migrations]
  (map->Database {:db-spec db-spec :skip-migrations skip-migrations}))

(defrecord WebServer [port app]
  component/Lifecycle

  (start [component]
    (let [p (or port (when-let [env-port (System/getenv "PORT")] (parse-long env-port)) 8000)]
      (println "Starting WebServer component on port" p "...")
      (assoc component
             ::jetty
             (run-jetty (-> component :app :handler)
                        {:port p
                         :join? false}))))

  (stop [component]
    (println "Stopping WebServer component...")
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
    (println "Starting App component...")
    (let [db-val (-> component :database :database)
          database (if (fn? db-val) (db-val) db-val)]
      (assoc component :handler
             (wrap-assoc handler :database database))))
  (stop [component]
    (println "Stopping App component...")
    component))

(defn new-app [handler]
  (component/using
   (map->App {:handler handler})
   [:database]))

(defn system [{:keys [db-spec skip-migrations]}]
  (component/system-map
   :database (new-database db-spec skip-migrations)
   :app      (new-app server/app)
   :server   (new-web-server)
   :scheduler (scheduler/new-scheduler)))


