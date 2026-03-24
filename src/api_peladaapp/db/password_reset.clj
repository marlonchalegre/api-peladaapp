(ns api-peladaapp.db.password-reset
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]))

(defn create-token! [user-id token expires-at db]
  (sql/insert! db :password_reset_tokens
               {:user_id user-id
                :token token
                :expires_at expires-at}))

(defn find-token [token db]
  (some-> (sql/get-by-id db :password_reset_tokens token :token {})
          misc/unamespace))

(defn delete-token! [token db]
  (sql/delete! db :password_reset_tokens {:token token}))

(defn delete-user-tokens! [user-id db]
  (sql/delete! db :password_reset_tokens {:user_id user-id}))

(defn delete-expired-tokens! [now db]
  (jdbc/execute! db ["DELETE FROM password_reset_tokens WHERE expires_at < ?" now]))
