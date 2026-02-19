(ns api-peladaapp.components
  (:require
   [api-peladaapp.server :as server]
   [com.stuartsierra.component :as component]
   [next.jdbc.connection :as connection]
   [ring.adapter.jetty :refer [run-jetty]])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(defrecord Database [database connection db-spec skip-migrations]
  component/Lifecycle
  (start [this]
    (println "Starting Database component...")
    (let [final-db-spec (merge {:dbtype "sqlite" :dbname "peladaapp.db"} db-spec)
          ds (connection/component HikariDataSource final-db-spec)]
      (assoc this :database (component/start ds))))
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
   :server   (new-web-server)))


