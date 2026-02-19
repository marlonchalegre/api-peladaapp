(ns api-peladaapp.server
  (:require
   [api-peladaapp.handlers.auth :as auth]
   [api-peladaapp.routes     :as routes]
   [buddy.auth.accessrules   :refer [wrap-access-rules]]
   [buddy.auth.middleware    :refer [wrap-authentication wrap-authorization]]
   [next.jdbc                :as jdbc]
   [ring.middleware.json     :refer [wrap-json-body wrap-json-response]]
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
(def ^:private db-spec {:dbtype "sqlite"
                        :dbname (or (System/getenv "DB_NAME") "peladaapp.db")})

(defonce ^:private datasource
  (jdbc/get-datasource db-spec))

(defn wrap-assoc [handler key value]
  (fn [request]
    ;; Only set the key if not already provided (e.g., tests inject :database)
    (let [request* (if (contains? request key)
                     request
                     (assoc request key value))]
      (handler request*))))

(def app (as-> #'routes/app-handler $
           (wrap-params $)
           (wrap-json-body $ {:keywords? true :bigdecimals? true})
           ;; Provide the DataSource directly
           (wrap-assoc $ :database datasource)
           (wrap-access-rules $ {:rules routes/access-rules :on-error on-error})
           (wrap-authorization $ auth/auth-backend)
           (wrap-authentication $ auth/auth-backend)
           (wrap-json-response $ {:charset "utf-8"})))
