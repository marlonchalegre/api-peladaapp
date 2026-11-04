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
   [buddy.auth.backends.token :refer [jws-backend]]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as str]))

(def token-cookie-name "authToken")
(def token-cookie-max-age (* 30 24 60 60))
(def cookie-secure? (not= (System/getenv "APP_VERSION") "development"))

(defn extract-token [request]
  (let [cookies (:cookies request)]
    (or (get-in cookies [token-cookie-name :value])
        (get-in cookies [(keyword token-cookie-name) :value]))))

(defn wrap-manual-auth
  "Custom authentication middleware that manually validates JWT from cookies or header.
  Bypasses buddy-auth's wrap-authentication which was failing on multipart requests."
  [handler]
  (fn [request]
    (if-let [token (extract-token request)]
      (try
        (let [secret (config/get-key :jwt-secret)
              claims (jwt/unsign token secret {:alg :hs512})
              ;; Ensure UUIDs are parsed back to objects
              claims (cond-> claims
                       (string? (:id claims)) (update :id parse-uuid)
                       (:admin_orgs claims) (update :admin_orgs #(map (fn [id] (if (string? id) (parse-uuid id) id)) %)))]
          (handler (assoc request :identity claims)))
        (catch Exception _
          (handler request)))
      (handler request))))

(defn set-token-cookie
  ([response token]
   (set-token-cookie response token token-cookie-max-age))
  ([response token max-age]
   (assoc-in response [:cookies token-cookie-name]
             {:value token
              :http-only true
              :secure cookie-secure?
              :same-site :lax
              :path "/"
              :max-age max-age})))

(defn wrap-sliding-session
  "Middleware that re-issues the authToken cookie with renewed max-age
  on successful responses for authenticated requests, so active users stay logged in."
  [handler]
  (fn [request]
    (let [response (handler request)
          status   (:status response)
          uri      (:uri request)]
      (if (and (authenticated? request)
               (number? status)
               (<= 200 status 299)
               (not (and uri (str/ends-with? uri "/avatar")))
               (not (or (get-in response [:cookies token-cookie-name])
                        (get-in response [:cookies (keyword token-cookie-name)]))))
        (if-let [token (extract-token request)]
          (set-token-cookie response token)
          response)
        response))))

(def auth-backend (jws-backend {:secret (fn [& _] (config/get-key :jwt-secret))
                                :token extract-token
                                :options {:alg :hs512}}))

;; Simple brute-force protection
(def login-attempts (atom {}))
(def password-reset-attempts (atom {}))
(def lockout-duration-ms (* 15 60 1000)) ;; 15 minutes

(defn- too-many-attempts? [attempts-atom identifier]
  (let [data (get @attempts-atom identifier)
        now (System/currentTimeMillis)]
    (if (and data (> (- now (:last-attempt data)) lockout-duration-ms))
      false ;; Lockout expired
      (>= (:count data 0) 5))))

(defn- record-failure [attempts-atom identifier]
  (swap! attempts-atom update identifier
         (fn [data]
           (let [now (System/currentTimeMillis)]
             (if (and data (> (- now (:last-attempt data)) lockout-duration-ms))
               {:count 1 :last-attempt now}
               {:count (inc (:count data 0)) :last-attempt now})))))

(defn- clear-attempts [attempts-atom identifier]
  (swap! attempts-atom dissoc identifier))

(defn auth-handler
  [request]
  (let [body (-> request :body)
        email (:email body)
        db (-> request :database)]
    (if (too-many-attempts? login-attempts email)
      (exception/api-exception-handler (ex-info "Too many login attempts. Please try again later."
                                                {:type :too-many-requests
                                                 :message "Too many login attempts. Please try again later."}))
      (try (let [{:keys [token user]} (-> body
                                          adapters.credential/login-request->model
                                          (controllers.auth/authenticate db))]
             (clear-attempts login-attempts email)
             (-> (adapters.credential/model->response token user)
                 ok
                 (set-token-cookie token)))
           (catch Exception e
             (record-failure login-attempts email)
             (exception/api-exception-handler e))))))

(defn logout-handler
  [_]
  (-> (ok {:message "Logged out successfully."})
      (set-token-cookie "" 0)))

(defn get-me-handler
  [request]
  (try (let [db (:database request)
             user-id (auth.logic/get-user-id-from-request request)]
         (ok (adapters.user/model->response (controllers.user/get-user user-id db) false)))
       (catch Exception e (exception/api-exception-handler e))))

(defn forgot-password-handler
  [request]
  (let [body (-> request :body)
        email (:email body)
        db (-> request :database)]
    (if (too-many-attempts? password-reset-attempts email)
      (exception/api-exception-handler (ex-info "Too many password reset attempts. Please try again later."
                                                {:type :too-many-requests
                                                 :message "Too many password reset attempts. Please try again later."}))
      (try
        (logic.password-reset/request-password-reset! email db)
        (record-failure password-reset-attempts email)
        (ok {:message "If an account with that email exists, we've sent a password reset link."})
        (catch Exception e
          (exception/api-exception-handler e))))))

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
               ok
               (set-token-cookie token)))
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
  "Check if request with JWT token has :is-global-admin? claim.
  Returns error with :forbidden type if user is authenticated but not admin."
  [request]
  (cond
    (not (authenticated? request))
    (error {:type :authentication
            :message "Authentication required. Please provide a valid token."})

    (not (and (:identity request)
              (:is-global-admin? (:identity request))))
    (error {:type :forbidden
            :message "Admin access required."})

    :else true))
