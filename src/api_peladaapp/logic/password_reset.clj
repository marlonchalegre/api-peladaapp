(ns api-peladaapp.logic.password-reset
  (:require
   [api-peladaapp.config :as config]
   [api-peladaapp.db.password-reset :as db.password-reset]
   [api-peladaapp.db.user :as db.user]
   [buddy.hashers :as hashers]
   [postal.core :as postal])
  (:import
   [java.time Instant]
   [java.time.temporal ChronoUnit]
   [java.util UUID]))

(defn- get-smtp-config []
  {:host (config/get-key :smtp-host)
   :port (config/get-key :smtp-port)
   :user (config/get-key :smtp-user)
   :pass (config/get-key :smtp-pass)
   :tls true})

(defn- generate-reset-link [token]
  (let [base-url (or (System/getenv "FRONTEND_URL") "http://localhost:5173")]
    (str base-url "/reset-password?token=" token)))

(defn send-reset-email! [email token]
  (let [reset-link (generate-reset-link token)
        smtp-config (get-smtp-config)]
    (try
      (postal/send-message smtp-config
                           {:from (config/get-key :smtp-user)
                            :to email
                            :subject "Password Reset - PeladaApp"
                            :body [{:type "text/plain"
                                    :content (str "Hello,\n\nYou requested a password reset. Please use the link below to reset your password:\n\n"
                                                  reset-link
                                                  "\n\nThis link will expire in 1 hour.\n\nIf you did not request this, please ignore this email.")}
                                   {:type "text/html"
                                    :content (str "<p>Hello,</p>"
                                                  "<p>You requested a password reset. Please use the link below to reset your password:</p>"
                                                  "<p><a href=\"" reset-link "\">Reset Password</a></p>"
                                                  "<p>This link will expire in 1 hour.</p>"
                                                  "<p>If you did not request this, please ignore this email.</p>")}]})
      (catch Exception e
        (println "ERROR: Failed to send reset email to" email ":" (.getMessage e))))))

(defn request-password-reset! [identifier db]
  (let [user (db.user/find-user-by-identifier identifier db)]
    (when (and user (:email user))
      (let [token (str (UUID/randomUUID))
            expires-at (str (-> (Instant/now)
                                (.plus 1 ChronoUnit/HOURS)))]
        (db.password-reset/delete-user-tokens! (:id user) db)
        (db.password-reset/create-token! (:id user) token expires-at db)
        (send-reset-email! (:email user) token)))))

(defn reset-password! [token new-password db]
  (let [token-data (db.password-reset/find-token token db)
        now (Instant/now)]
    (if (and token-data
             (.isAfter (Instant/parse (:expires_at token-data)) now))
      (let [user-id (:user_id token-data)
            hashed-password (hashers/encrypt new-password)]
        (db.user/update-user user-id {:password hashed-password} db)
        (db.password-reset/delete-user-tokens! user-id db)
        true)
      false)))
