(ns api-peladaapp.db.match-lineup
  (:require
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.team :as db.team]
   [api-peladaapp.helpers.sql :as hsql]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(defn- unqualify-row [row]
  (into {}
        (map (fn [[k v]]
               (let [kw (if (keyword? k) (keyword (name k)) k)]
                 [kw v])))
        row))

(s/defn list-by-match :- [s/Any]
  [match-id :- s/Uuid db]
  (let [query (-> (h/select :*)
                  (h/from :MatchLineups)
                  (h/where [:= :match_id match-id]))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map unqualify-row))))

(s/defn list-by-match-grouped :- {s/Uuid [s/Any]}
  [match-id :- s/Uuid db]
  (let [rows (list-by-match match-id db)]
    (reduce (fn [acc row]
              (let [team-id (:team_id row)]
                (update acc team-id (fnil conj []) row)))
            {} rows)))

(s/defn ensure-seeded :- s/Int
  "Seed lineup from team players for both sides if match has no lineup rows yet.
   Returns number of rows inserted (may be 0)."
  [match-id :- s/Uuid db]
  (let [existing (list-by-match match-id db)]
    (if (seq existing)
      0
      (let [m (db.match/get-match match-id db)
            pelada (db.pelada/get-pelada (:pelada-id m) db)
            home (:home-team-id m)
            away (:away-team-id m)
            home-players (db.team/list-team-players home db)
            away-players (db.team/list-team-players away db)

            ;; Base outfield players
            outfield (concat (map (fn [p] {:match_id match-id :team_id home :player_id (:player-id p) :is_goalkeeper (boolean (:is-goalkeeper p))}) home-players)
                             (map (fn [p] {:match_id match-id :team_id away :player_id (:player-id p) :is_goalkeeper (boolean (:is-goalkeeper p))}) away-players))

            ;; Add fixed goalkeepers if enabled
            ;; Using a map keyed by player_id to ensure uniqueness
            lineup-map (reduce (fn [acc p] (assoc acc (:player_id p) p))
                               {}
                               outfield)

            final-lineup (cond-> lineup-map
                           (and (:fixed-goalkeepers pelada) (:home-fixed-goalkeeper-id pelada))
                           (assoc (:home-fixed-goalkeeper-id pelada)
                                  {:match_id match-id :team_id home :player_id (:home-fixed-goalkeeper-id pelada) :is_goalkeeper true})

                           (and (:fixed-goalkeepers pelada) (:away-fixed-goalkeeper-id pelada))
                           (assoc (:away-fixed-goalkeeper-id pelada)
                                  {:match_id match-id :team_id away :player_id (:away-fixed-goalkeeper-id pelada) :is_goalkeeper true}))

            to-insert (vals final-lineup)]
        (if (empty? to-insert)
          0
          (try
            (let [query (-> (h/insert-into :MatchLineups)
                            (h/values to-insert))]
              (hsql/affected-rows-count (jdbc/execute-one! db (hsql/format query) hsql/opts)))
            (catch Exception _ 0)))))))

(s/defn add-player :- s/Int
  [match-id :- s/Uuid team-id :- s/Uuid player-id :- s/Uuid db]
  (try
    (let [query (-> (h/insert-into :MatchLineups)
                    (h/values [{:match_id match-id :team_id team-id :player_id player-id}]))]
      (hsql/affected-rows-count (jdbc/execute-one! db (hsql/format query) hsql/opts)))
    (catch Exception _
      0)))

(s/defn remove-player :- s/Int
  [match-id :- s/Uuid team-id :- s/Uuid player-id :- s/Uuid db]
  (let [query (-> (h/delete-from :MatchLineups)
                  (h/where [:and [:= :match_id match-id] [:= :team_id team-id] [:= :player_id player-id]]))]
    (-> (jdbc/execute-one! db (hsql/format query) hsql/opts)
        hsql/affected-rows-count)))

(s/defn replace-player :- s/Int
  [match-id :- s/Uuid team-id :- s/Uuid out-player-id :- s/Uuid in-player-id :- s/Uuid db]
  (let [is-gk (db.team/is-goalkeeper? team-id out-player-id db)
        rm (remove-player match-id team-id out-player-id db)
        ;; If they were a goalkeeper, the incoming player should also be one
        ad (try
             (let [query (-> (h/insert-into :MatchLineups)
                             (h/values [{:match_id match-id :team_id team-id :player_id in-player-id :is_goalkeeper (boolean is-gk)}]))]
               (hsql/affected-rows-count (jdbc/execute-one! db (hsql/format query) hsql/opts)))
             (catch Exception _ 0))]
    (+ rm ad)))

(s/defn list-match-lineups-by-pelada :- [s/Any]
  [pelada-id :- s/Uuid db]
  (let [query (-> (h/select :ml.*)
                  (h/from [:MatchLineups :ml])
                  (h/join [:Matches :m] [:= :ml.match_id :m.id])
                  (h/where [:= :m.pelada_id pelada-id]))]
    (->> (jdbc/execute! db (hsql/format query) hsql/opts)
         (map unqualify-row)
         vec)))
