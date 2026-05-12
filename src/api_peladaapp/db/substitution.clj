(ns api-peladaapp.db.substitution
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [schema.core :as s]))

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(s/defn insert-substitution :- s/Int
  [{:keys [match-id minute out-player-id in-player-id]}
   db]
  (let [query (-> (h/insert-into :MatchSubstitutions)
                  (h/values [{:match_id match-id
                              :minute minute
                              :out_player_id out-player-id
                              :in_player_id in-player-id}]))]
    (:id (jdbc/execute-one! db (hsql/format query) opts))))

(s/defn list-substitutions [match-id db]
  (let [query (-> (h/select :*)
                  (h/from :MatchSubstitutions)
                  (h/where [:= :match_id match-id]))]
    (jdbc/execute! db (hsql/format query) opts)))
