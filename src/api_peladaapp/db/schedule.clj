(ns api-peladaapp.db.schedule
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn list-match-plans-by-pelada [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :PeladaMatchPlans)
                  (h/where [:= :pelada_id pelada-id])
                  (h/order-by :sequence))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))

(s/defn delete-match-plans-by-pelada [pelada-id :- s/Uuid db]
  (let [query (-> (h/delete-from :PeladaMatchPlans)
                  (h/where [:= :pelada_id pelada-id]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn insert-match-plan [{:keys [pelada-id home-team-id away-team-id sequence]} :- {:pelada-id s/Uuid
                                                                                      :home-team-id s/Uuid
                                                                                      :away-team-id s/Uuid
                                                                                      :sequence s/Int}
                           db]
  (let [query (-> (h/insert-into :PeladaMatchPlans)
                  (h/values [{:pelada_id pelada-id
                              :home_team_id home-team-id
                              :away_team_id away-team-id
                              :sequence sequence}])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))
