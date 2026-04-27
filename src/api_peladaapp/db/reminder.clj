(ns api-peladaapp.db.reminder
  (:require
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(s/defn insert-reminder! :- s/Int
  [pelada-id :- s/Int
   type :- s/Str
   db]
  (-> (sql/insert! db :PeladaReminders {:pelada_id pelada-id :type type})
      vals
      first))

(s/defn get-last-reminder-at :- (s/maybe s/Str)
  [pelada-id :- s/Int
   type :- s/Str
   db]
  (-> (sql/query db ["SELECT sent_at FROM PeladaReminders 
                      WHERE pelada_id = ? AND type = ? 
                      ORDER BY sent_at DESC LIMIT 1" pelada-id type]
                 {:builder-fn rs/as-unqualified-lower-maps})
      first
      :sent_at))
