(ns api-peladaapp.handlers.auth
  (:require
   [api-peladaapp.adapters.credential :as adapters.credential]
   [api-peladaapp.adapters.user :as adapters.user]
   [api-peladaapp.config :as config]
   [api-peladaapp.controllers.auth :as controllers.auth]
   [api-peladaapp.controllers.user :as controllers.user]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [ok]]
   [api-peladaapp.logic.authorization :as auth.logic]
   [api-peladaapp.logic.password-reset :as logic.password-reset]
   [buddy.auth :refer [authenticated?]]
   [buddy.auth.accessrules :refer [error]]
   [buddy.auth.backends.token :refer [jws-backend]]))

(def auth-backend (jws-backend {:secret (config/get-key :jwt-secret)
                                :token-name "Token"
                                :options {:alg :hs512}}))

;; Simple brute-force protection
(def login-attempts (atom {}))

(defn- too-many-attempts? [email]
  (let [attempts (get @login-attempts email 0)]
    (>= attempts 5)))

(defn- record-failure [email]
  (swap! login-attempts update email (fnil inc 0)))

(defn- clear-attempts [email]
  (swap! login-attempts dissoc email))

(defn auth-handler
  [request]
  (let [body (-> request :body)
        email (:email body)
        db (-> request :database)]
    (if (too-many-attempts? email)
      (exception/api-exception-handler (ex-info "Too many login attempts. Please try again later."
                                                {:type :too-many-requests
                                                 :message "Too many login attempts. Please try again later."}))
      (try (let [{:keys [token user]} (-> body
                                          adapters.credential/login-request->model
                                          (controllers.auth/authenticate db))]
             (clear-attempts email)
             (-> (adapters.credential/model->response token user)
                 ok))
           (catch Exception e
             (record-failure email)
             (exception/api-exception-handler e))))))

(defn get-me-handler
  [request]
  (try (let [db (:database request)
             user-id (auth.logic/get-user-id-from-request request)]
         (ok (adapters.user/model->response (controllers.user/get-user user-id db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn forgot-password-handler
  [request]
  (let [body (-> request :body)
        email (:email body)
        db (-> request :database)]
    (try
      (logic.password-reset/request-password-reset! email db)
      (ok {:message "If an account with that email exists, we've sent a password reset link."})
      (catch Exception e
        (exception/api-exception-handler e)))))

(defn reset-password-handler
  [request]
  (let [body (-> request :body)
        token (:token body)
        password (:password body)
        db (-> request :database)]
    (try
      (if (logic.password-reset/reset-password! token password db)
        (ok {:message "Password reset successfully."})
        (exception/api-exception-handler (ex-info "Invalid or expired token."
                                                  {:type :bad-request
                                                   :message "Invalid or expired token."})))
      (catch Exception e
        (exception/api-exception-handler e)))))

(defn first-access-handler
  [request]
  (let [body (-> request :body)
        db (-> request :database)]
    (try (let [{:keys [token user]} (controllers.auth/first-access body db)]
           (-> (adapters.credential/model->response token user)
               ok))
         (catch Exception e
           (exception/api-exception-handler e)))))

;; Access Level Handlers

(defn authenticated-access
  "Check if request coming in is authenticated with a valid JWT token.
  Returns error with :authentication type if not authenticated."
  [request]
  (if (authenticated? request)
    true
    (error {:type :authentication
            :message "Authentication required. Please provide a valid token."})))

(defn admin-access
  "Check if request with JWT token has :is-admin? claim.
  Returns error with :forbidden type if user is authenticated but not admin."
  [request]
  (cond
    (not (authenticated? request))
    (error {:type :authentication
            :message "Authentication required. Please provide a valid token."})

    (not (and (:identity request)
              (:is-admin? (:identity request))))
    (error {:type :forbidden
            :message "Admin access required."})

    :else true))