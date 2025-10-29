(ns api-peladaapp.server
  (:require
   [api-peladaapp.handlers.auth :as auth]
   [api-peladaapp.routes     :as routes]
   [buddy.auth.accessrules   :refer [wrap-access-rules]]
   [buddy.auth.middleware    :refer [wrap-authentication wrap-authorization]]
   [migratus.core            :as migratus]
   [next.jdbc                :as jdbc]
   [clojure.string           :as str]
   [clojure.java.io          :as io]
   [ring.middleware.json     :refer [wrap-json-body wrap-json-response]])
  (:gen-class))

(defn on-error
  "Handle authentication and authorization errors.
  Returns 401 for authentication failures (missing/invalid token)
  and 403 for authorization failures (valid token but insufficient permissions)."
  [_request value]
  (let [error-type (or (:type value) :forbidden)
        status (case error-type
                 :authentication 401
                 :unauthorized 401
                 :forbidden 403
                 403)
        message (or (:message value) 
                    (case error-type
                      :authentication "Authentication required"
                      :unauthorized "Authentication required"
                      :forbidden "Access forbidden"
                      "Access denied"))]
    {:status status
     :headers {"Content-Type" "application/json; charset=utf-8"}
     :body {:error message
            :type (name error-type)}}))

;; In dev (lein-ring), we don't start the Component system, so we must
;; initialize the database and inject it into every request ourselves.
(def ^:private db-spec {:dbtype "sqlite" :dbname "peladaapp.db"})

(defn- run-sql-file! [ds path]
  (let [res    (or (io/resource path) (io/file path))
        content (slurp res)
        statements (->> (str/split content #";[\r\n]+")
                        (map str/trim)
                        (remove str/blank?))]
    (with-open [conn (jdbc/get-connection ds)]
      (doseq [stmt statements]
        (jdbc/execute! conn [stmt])))))

(defonce ^:private datasource
  (let [ds (jdbc/get-datasource db-spec)]
    ;; Try migratus, then ensure schema by applying the SQL file idempotently
    (try
      (migratus/migrate {:store :database
                         :migration-dir "migrations"
                         :db db-spec})
      (catch Exception _
        ;; ignore, fall through to manual ensure
        ))
    (try
      ;; Prefer classpath resource path
      (run-sql-file! ds "migrations/20251028150000-init_all.up.sql")
      (catch Exception _
        (try
          ;; Fallback to relative file path
          (run-sql-file! ds "resources/migrations/20251028150000-init_all.up.sql")
          (catch Exception _
            ;; ignore if file not found; best-effort
            ))))
    ds))

(defn- wrap-assoc [handler key value]
  (fn [request]
    ;; Only set the key if not already provided (e.g., tests inject :database)
    (let [request* (if (contains? request key)
                     request
                     (assoc request key value))]
      (handler request*))))

(def app (as-> #'routes/app-handler $
           (wrap-json-body $ {:keywords? true :bigdecimals? true})
           ;; Provide a zero-arg function returning the DataSource, matching tests/handlers expectations
           (wrap-assoc $ :database (fn [] datasource))
           (wrap-access-rules $ {:rules routes/access-rules :on-error on-error})
           (wrap-authorization $ auth/auth-backend)
           (wrap-authentication $ auth/auth-backend)
           (wrap-json-response $ {:charset "utf-8"})))
