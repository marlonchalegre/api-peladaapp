(ns api-peladaapp.server
  (:require
   [api-peladaapp.handlers.auth :as auth]
   [api-peladaapp.routes     :as routes]
   [buddy.auth.accessrules   :refer [wrap-access-rules]]
   [buddy.auth.middleware    :refer [wrap-authorization]]
   [clojure.string           :as str]
   [clojure.tools.logging    :as log]
   [next.jdbc                :as jdbc]
   [ring.middleware.cookies  :refer [wrap-cookies]]
   [ring.middleware.json     :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.params   :refer [wrap-params]])
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
                 :invalid-credentials 401
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
(def ^:private db-spec
  (delay
    (let [database-url (System/getenv "DATABASE_URL")]
      (if database-url
        (let [[_ user pass host port db] (if (str/starts-with? database-url "postgres://")
                                           (re-matches #"^postgres://([^:]+):(.+)@([^@:]+)(?::(\d+))?/(.+)$" database-url)
                                           nil)]
          (if host
            {:dbtype "postgresql"
             :dbname db
             :host host
             :port (if port (Integer/parseInt port) 5432)
             :user user
             :password pass}
            (throw (Exception. "Invalid DATABASE_URL format."))))
        (throw (Exception. "DATABASE_URL is required for dev database initialization. PostgreSQL is now mandatory."))))))

(defonce ^:private datasource
  (delay (jdbc/get-datasource @db-spec)))

(defn wrap-assoc [handler key value-delay]
  (fn [request]
    ;; Only set the key if not already provided (e.g., tests inject :database)
    (let [request* (if (contains? request key)
                     request
                     (assoc request key @value-delay))]
      (handler request*))))

(defn wrap-exception-log [handler]
  (fn [request]
    (try
      (let [response (handler request)]
        (when (= 500 (:status response))
          (log/error "[SERVER 500]" (:uri request) (:body response)))
        response)
      (catch Throwable e
        (log/error e "[FATAL ERROR]" (:uri request))
        (throw e)))))

(def app (as-> #'routes/app-handler $
           (wrap-exception-log $)
           ;; Provide the DataSource directly
           (wrap-assoc $ :database datasource)
           (wrap-multipart-params $)
           (wrap-params $)
           (wrap-json-body $ {:keywords? true :bigdecimals? true})
           (wrap-access-rules $ {:rules routes/access-rules :on-error on-error})
           (wrap-authorization $ auth/auth-backend)
           (auth/wrap-manual-auth $)
           (wrap-cookies $)
           (wrap-json-response $ {:charset "utf-8"})))
