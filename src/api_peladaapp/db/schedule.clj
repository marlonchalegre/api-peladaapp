(ns api-peladaapp.db.schedule
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn list-match-plans-by-pelada [pelada-id db]
  (let [query (-> (h/select :*)
                  (h/from :PeladaMatchPlans)
                  (h/where [:= :pelada_id pelada-id])
                  (h/order-by :sequence))]
    (->> (jdbc/execute! db (hsql/format query) opts))))

(s/defn delete-match-plans-by-pelada [pelada-id db]
  (let [query (-> (h/delete-from :PeladaMatchPlans)
                  (h/where [:= :pelada_id pelada-id]))]
    (jdbc/execute-one! db (hsql/format query))))

(s/defn insert-match-plan [{:keys [pelada-id home-team-id away-team-id sequence]} db]
  (let [query (-> (h/insert-into :PeladaMatchPlans)
                  (h/values [{:pelada_id pelada-id
                              :home_team_id home-team-id
                              :away_team_id away-team-id
                              :sequence sequence}]))]
    (jdbc/execute-one! db (hsql/format query))))
