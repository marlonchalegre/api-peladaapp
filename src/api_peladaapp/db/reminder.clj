(ns api-peladaapp.db.reminder
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn insert-reminder! :- s/Uuid
  [pelada-id :- s/Uuid
   type :- s/Str
   db]
  (let [query (-> (h/insert-into :PeladaReminders)
                  (h/values [{:pelada_id (misc/as-uuid pelada-id) :type [:cast type :reminder_type]}])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))

(s/defn get-last-reminder-at :- (s/maybe s/Str)
  [pelada-id :- s/Uuid
   type :- s/Str
   db]
  (let [query (-> (h/select :sent_at)
                  (h/from :PeladaReminders)
                  (h/where [:= :pelada_id (misc/as-uuid pelada-id)] [:= :type [:cast type :reminder_type]])
                  (h/order-by [:sent_at :desc])
                  (h/limit 1))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        :sent_at)))
