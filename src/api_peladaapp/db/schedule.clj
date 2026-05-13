(ns api-peladaapp.db.schedule
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(defn- affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn list-match-plans-by-pelada [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :PeladaMatchPlans)
                  (h/where [:= :pelada_id pelada-id])
                  (h/order-by :sequence))]
    (->> (jdbc/execute! db (hsql/format query) opts))))

(s/defn delete-match-plans-by-pelada [pelada-id :- s/Uuid db]
  (let [query (-> (h/delete-from :PeladaMatchPlans)
                  (h/where [:= :pelada_id pelada-id]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))

(s/defn insert-match-plan [{:keys [pelada-id home-team-id away-team-id sequence]} :- {:pelada-id s/Uuid
                                                                                      :home-team-id s/Uuid
                                                                                      :away-team-id s/Uuid
                                                                                      :sequence s/Int}
                           db]
  (let [query (-> (h/insert-into :PeladaMatchPlans)
                  (h/values [{:pelada_id pelada-id
                              :home_team_id home-team-id
                              :away_team_id away-team-id
                              :sequence sequence}]))]
    (-> (jdbc/execute-one! db (hsql/format query) opts)
        affected-rows-count)))
