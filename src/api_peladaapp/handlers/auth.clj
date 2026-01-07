(ns api-peladaapp.handlers.auth
  (:require
   [api-peladaapp.adapters.credential :as adapters.credential]
   [api-peladaapp.config :as config]
   [api-peladaapp.controllers.auth :as controllers.auth]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [ok]]
   [buddy.auth :refer [authenticated?]]
   [buddy.auth.accessrules :refer [error]]
   [buddy.auth.backends.token :refer [jws-backend]]))

(def auth-backend (jws-backend {:secret (config/get-key :jwt-secret)
                                :token-name "Token"
                                :options {:alg :hs512}}))

(defn auth-handler
  [request]
  (let [body (-> request :body)
        db (-> request :database)]
    (try (let [{:keys [token user]} (-> body
                                        adapters.credential/login-request->model
                                        (controllers.auth/authenticate db))]
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