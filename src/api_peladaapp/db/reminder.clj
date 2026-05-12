(ns api-peladaapp.db.reminder
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-reminder! :- s/Int
  [pelada-id :- s/Int
   type :- s/Str
   db]
  (let [query (-> (h/insert-into :PeladaReminders)
                  (h/values [{:pelada_id pelada-id :type type}]))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn get-last-reminder-at :- (s/maybe s/Str)
  [pelada-id :- s/Int
   type :- s/Str
   db]
  (let [query (-> (h/select :sent_at)
                  (h/from :PeladaReminders)
                  (h/where [:= :pelada_id pelada-id] [:= :type type])
                  (h/order-by [:sent_at :desc])
                  (h/limit 1))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        :sent_at)))
