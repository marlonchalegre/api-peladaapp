(ns api-100folego.handlers.auth
  (:require
   [api-100folego.adapters.credential :as adapters.credential]
   [api-100folego.config :as config]
   [api-100folego.controllers.auth :as controllers.auth]
   [api-100folego.helpers.exception :as exception]
   [api-100folego.helpers.responses :refer [ok]]
   [buddy.auth :refer [authenticated?]]
   [buddy.auth.accessrules :refer [error]]
   [buddy.auth.backends.token :refer [jws-backend]]))

(def auth-backend (jws-backend {:secret (config/get-key :jwt-secret)
                                :token-name "Token"
                                :options {:alg :hs512}}))

;; (defn authenticate
;;   "Checks if request (with username/password :query-params)
;;   or username/password is valid"
;;   ([request]
;;    (let [username (get-in request [:body :username])
;;          password (get-in request [:body :password])]
;;      (authenticate username password)))
;;   ([username password]
;;    (if (and username password)
;;      true #_(user/login? username password)
;;      false)))

(defn auth-handler
  [request]
  (let [body (-> request :body)
        db (-> request :database)]
    (try (-> body
             adapters.credential/in->model
             (controllers.auth/authenticate db)
             adapters.credential/->out
             ok)
         (catch Exception e
           (exception/api-exception-handler e)))))

;; Access Level Handlers

(defn authenticated-access
  "Check if request coming in is authenticated with a valid JWT token"
  [request]
  (if (authenticated? request)
    true
    (error "access not allowed")))

(defn admin-access
  "Check if request with JWT token has :is-admin? claim"
  [request]
  (if (and (:identity request)
           (:is-admin? (:identity request)))
    true
    (error "requires admin access")))
