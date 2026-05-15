(ns api-peladaapp.db.attendance
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn upsert-attendance :- s/Int
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   status :- s/Str
   db]
  (let [now (java.sql.Timestamp. (System/currentTimeMillis))
        query (-> (h/insert-into :Attendance)
                  (h/values [{:pelada_id pelada-id :player_id player-id :status [:cast status :attendance_status] :updated_at now}])
                  (h/on-conflict :pelada_id :player_id)
                  (h/do-update-set :status :updated_at))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn batch-upsert-attendance :- s/Int
  [pelada-id :- s/Uuid
   player-ids :- [s/Uuid]
   status :- s/Str
   db]
  (if (empty? player-ids)
    0
    (let [now (java.sql.Timestamp. (System/currentTimeMillis))
          player-ids (vec player-ids)
          rows (vec (map (fn [pid] {:pelada_id pelada-id :player_id pid :status [:cast status :attendance_status] :updated_at now}) player-ids))
          query (-> (h/insert-into :Attendance)
                    (h/values rows)
                    (h/on-conflict :pelada_id :player_id)
                    (h/do-update-set :status :updated_at))]
      (jdbc/with-transaction [tx db]
        (-> (jdbc/execute-one! tx (hsql/format query) hsql/opts)
            hsql/affected-rows-count)))))

(s/defn list-attendance-by-pelada :- [s/Any]
  [pelada-id :- s/Uuid
   db]
  (let [query (-> (h/select :*)
                  (h/from :Attendance)
                  (h/where [:= :pelada_id pelada-id]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn list-pending-attendance-by-pelada [pelada-id :- s/Uuid db]
  (let [query (-> (h/select [:op.id :player_id] [:u.name :player_name] :u.phone)
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/join [:Peladas :p] [:= :op.organization_id :p.organization_id])
                  (h/where [:and
                            [:= :p.id pelada-id]
                            [:not-exists (-> (h/select 1)
                                             (h/from [:Attendance :pa])
                                             (h/where [:and [:= :pa.pelada_id :p.id] [:= :pa.player_id :op.id]]))]]))
        results (jdbc/execute! db (hsql/format query) hsql/opts)]
    (map (fn [r] {:player-id (:player_id r) :player-name (:player_name r) :phone (:phone r)}) results)))

(s/defn list-pending-mensalistas-by-pelada [pelada-id :- s/Uuid db]
  (let [query (-> (h/select [:op.id :player_id] [:u.name :player_name] :u.phone)
                  (h/from [:OrganizationPlayers :op])
                  (h/join [:Users :u] [:= :op.user_id :u.id])
                  (h/join [:Peladas :p] [:= :op.organization_id :p.organization_id])
                  (h/where [:and
                            [:= :p.id pelada-id]
                            [:in :op.member_type [[:cast "mensalista" :member_type] [:cast "mensalista_temporario" :member_type]]]
                            [:not-exists (-> (h/select 1)
                                             (h/from [:Attendance :pa])
                                             (h/where [:and
                                                       [:= :pa.pelada_id :p.id]
                                                       [:= :pa.player_id :op.id]
                                                       [:in :pa.status [[:cast "confirmed" :attendance_status]
                                                                        [:cast "declined" :attendance_status]
                                                                        [:cast "waitlist" :attendance_status]]]]))]]))
        results (jdbc/execute! db (hsql/format query) hsql/opts)]
    (map (fn [r] {:player-id (:player_id r) :player-name (:player_name r) :phone (:phone r)}) results)))

(s/defn delete-attendance :- s/Int
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [query (-> (h/delete-from :Attendance)
                  (h/where [:and [:= :pelada_id pelada-id] [:= :player_id player-id]]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn update-voting-enabled :- s/Int
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   enabled? :- s/Bool
   db]
  (let [query (-> (h/update :Attendance)
                  (h/set {:voting_enabled (boolean enabled?)})
                  (h/where [:and [:= :pelada_id pelada-id] [:= :player_id player-id]]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))
