(ns api-peladaapp.components
  (:require
   [api-peladaapp.server :as server]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [next.jdbc :as jdbc]
   [ring.adapter.jetty :refer [run-jetty]])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(defn- redact-jdbc-url [url]
  (if (str/blank? url)
    url
    (-> url
        (str/replace #"password=[^&]*" "password=***")
        (str/replace #"//([^:]+):([^@]+)@" "//***:***@"))))

(defn- add-schema-to-url [jdbc-url schema]
  (if (or (str/blank? schema) (= "public" schema))
    jdbc-url
    (let [separator (if (str/includes? jdbc-url "?") "&" "?")]
      (if (str/includes? jdbc-url "currentSchema=")
        jdbc-url ;; Already has it
        (str jdbc-url separator "currentSchema=" schema)))))

(defrecord Database [db-spec skip-migrations datasource database]
  component/Lifecycle
  (start [this]
    (if (or datasource database)
      (assoc this :datasource (or datasource database) :database (or database datasource))
      (let [database-url (System/getenv "DATABASE_URL")
            schema (or (System/getenv "DB_SCHEMA") "public")

            final-db-spec (cond
                            (or (:jdbcUrl db-spec) (:dbtype db-spec))
                            (do
                              (try (Class/forName "org.postgresql.Driver") (catch Exception _))
                              (assoc db-spec :connectionTestQuery "SELECT 1" :maximumPoolSize 10))

                          ;; Postgres via DATABASE_URL
                            database-url
                            (if (str/starts-with? database-url "postgres://")
                              (let [[_ user pass host port db] (re-matches #"^postgres://([^:]+):(.+)@([^@:]+)(?::(\d+))?/(.+)$" database-url)]
                                (if host
                                  (let [jdbc-url (add-schema-to-url (str "jdbc:postgresql://" host ":" (if port port 5432) "/" db) schema)]
                                    (log/info (str "Using DATABASE_URL with schema '" schema "'"))
                                    (try (Class/forName "org.postgresql.Driver") (catch Exception _))
                                    {:jdbcUrl jdbc-url
                                     :user user
                                     :password pass
                                     :connectionTestQuery "SELECT 1"})
                                  (throw (Exception. "Invalid DATABASE_URL format"))))
                              (let [jdbc-url (add-schema-to-url database-url schema)]
                                (log/info (str "Using DATABASE_URL with schema '" schema "': " (redact-jdbc-url jdbc-url)))
                                (try (Class/forName "org.postgresql.Driver") (catch Exception _))
                                {:jdbcUrl jdbc-url
                                 :connectionTestQuery "SELECT 1"}))

                          ;; Fallback
                            :else
                            (throw (Exception. "No DATABASE_URL found and no db-spec provided. PostgreSQL is now mandatory.")))

            ds (jdbc/get-datasource final-db-spec)]
        (assoc this :datasource ds :database ds))))
  (stop [this]
    (when (and datasource (instance? HikariDataSource datasource))
      (.close ^HikariDataSource datasource))
    (assoc this :datasource nil :database nil)))

(defn new-database [db-spec skip-migrations]
  (map->Database {:db-spec db-spec :skip-migrations skip-migrations}))

(defrecord WebServer [app port server]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [port (or (some-> (System/getenv "PORT") Integer/parseInt) 8000)
            server (run-jetty (:app-handler app) {:port port :join? false})]
        (log/info (str "Web server started on port " port))
        (assoc this :port port :server server))))
  (stop [this]
    (if server
      (do
        (.stop server)
        (assoc this :server nil))
      this)))

(defn new-web-server []
  (component/using
   (map->WebServer {})
   [:app]))

(defrecord App [handler database]
  component/Lifecycle
  (start [this]
    (if (:app-handler this)
      this
      (let [db-val (:database database)]
        (log/info "Starting App component with database context...")
        (assoc this :app-handler
               (fn [request]
                 (handler (assoc request :database db-val)))))))
  (stop [this]
    (log/info "Stopping App component...")
    (assoc this :app-handler nil)))

(defn new-app [handler]
  (component/using
   (map->App {:handler handler})
   [:database]))

(defn system [{:keys [db-spec skip-migrations]}]
  (component/system-map
   :database (new-database db-spec skip-migrations)
   :app      (new-app server/app)
   :server   (new-web-server)))
