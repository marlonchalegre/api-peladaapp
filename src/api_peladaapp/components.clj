(ns api-peladaapp.components
  (:require
   [api-peladaapp.server :as server]
   [com.stuartsierra.component :as component]
   [migratus.core :as migratus]
   [next.jdbc.connection :as connection]
   [ring.adapter.jetty :refer [run-jetty]])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(defrecord Database [database connection db-spec skip-migrations]
  component/Lifecycle
  (start [this]
    (let [final-db-spec (merge {:dbtype "sqlite" :dbname "peladaapp.db"} db-spec)
          ds (connection/component HikariDataSource final-db-spec)]
      (when-not skip-migrations
        (migratus/migrate {:store :database
                           :migration-dir "migrations"
                           :db final-db-spec}))
      (assoc this :database (component/start ds))))
  (stop [this]
    (when-let [ds (:database this)]
      (component/stop ds))
    (assoc this :database nil)))

(defn new-database [db-spec skip-migrations]
  (map->Database {:db-spec db-spec :skip-migrations skip-migrations}))

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
    (let [db-val (-> component :database :database)
          database (if (fn? db-val) (db-val) db-val)]
      (assoc component :handler
             (wrap-assoc handler :database database))))
  (stop [component]
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


