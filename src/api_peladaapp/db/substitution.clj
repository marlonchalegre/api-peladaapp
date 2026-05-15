(ns api-peladaapp.db.substitution
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn insert-substitution :- s/Uuid
  [{:keys [match-id minute out-player-id in-player-id]} :- {:match-id s/Uuid
                                                            :minute s/Int
                                                            :out-player-id s/Uuid
                                                            :in-player-id s/Uuid}
   db]
  (let [query (-> (h/insert-into :MatchSubstitutions)
                  (h/values [{:match_id match-id
                              :minute minute
                              :out_player_id out-player-id
                              :in_player_id in-player-id}])
                  (h/returning :id))]
    (:id (jdbc/execute-one! db (hsql/format query) hsql/opts))))

(s/defn list-substitutions [match-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :MatchSubstitutions)
                  (h/where [:= :match_id match-id]))]
    (jdbc/execute! db (hsql/format query) hsql/opts)))
