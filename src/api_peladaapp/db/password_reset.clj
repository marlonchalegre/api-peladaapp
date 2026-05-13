(ns api-peladaapp.db.password-reset
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn create-token! [user-id :- s/Uuid token :- s/Str expires-at db]
  (let [query (-> (h/insert-into :password_reset_tokens)
                  (h/values [{:user_id user-id
                              :token token
                              :expires_at [:cast expires-at :timestamptz]}]))]
    (jdbc/execute-one! db (hsql/format query))))

(s/defn find-token [token :- s/Str db]
  (let [query (-> (h/select :*)
                  (h/from :password_reset_tokens)
                  (h/where [:= :token token]))]
    (some-> (jdbc/execute-one! db (hsql/format query) opts)
            misc/unamespace)))

(s/defn delete-token! [token :- s/Str db]
  (let [query (-> (h/delete-from :password_reset_tokens)
                  (h/where [:= :token token]))]
    (jdbc/execute-one! db (hsql/format query))))

(s/defn delete-user-tokens! [user-id :- s/Uuid db]
  (let [query (-> (h/delete-from :password_reset_tokens)
                  (h/where [:= :user_id user-id]))]
    (jdbc/execute-one! db (hsql/format query))))

(s/defn delete-expired-tokens! [now db]
  (let [query (-> (h/delete-from :password_reset_tokens) (h/where [:< :expires_at now]))]
    (jdbc/execute! db (hsql/format query))))
