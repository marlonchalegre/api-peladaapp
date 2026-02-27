(ns api-peladaapp.db.match-lineup
  (:require
   [api-peladaapp.db.match :as db.match]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.team :as db.team]
   [next.jdbc.sql :as sql]
   [schema.core :as s]))

(defn- unqualify-row [row]
  (into {}
        (map (fn [[k v]]
               (let [kw (if (keyword? k) (keyword (name k)) k)]
                 [kw v])))
        row))

(defn- affected-rows-count [result]
  (-> result vals first))

(s/defn list-by-match :- [s/Any]
  [match-id db]
  (->> (sql/find-by-keys db :matchlineups {:match_id match-id})
       (map unqualify-row)))

(s/defn list-by-match-grouped :- {s/Int [s/Any]}
  [match-id db]
  (let [rows (list-by-match match-id db)]
    (reduce (fn [acc row]
              (let [team-id (:team_id row)]
                (update acc team-id (fnil conj []) row)))
            {} rows)))

(s/defn ensure-seeded :- s/Int
  "Seed lineup from team players for both sides if match has no lineup rows yet.
   Returns number of rows inserted (may be 0)."
  [match-id db]
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
            outfield (concat (map (fn [p] {:match_id match-id :team_id home :player_id (:player-id p) :is_goalkeeper 0}) home-players)
                             (map (fn [p] {:match_id match-id :team_id away :player_id (:player-id p) :is_goalkeeper 0}) away-players))

            ;; Add fixed goalkeepers if enabled
            ;; Using a map keyed by player_id to ensure uniqueness
            lineup-map (reduce (fn [acc p] (assoc acc (:player_id p) p))
                               {}
                               outfield)

            final-lineup (cond-> lineup-map
                           (and (:fixed-goalkeepers pelada) (:home-fixed-goalkeeper-id pelada))
                           (assoc (:home-fixed-goalkeeper-id pelada)
                                  {:match_id match-id :team_id home :player_id (:home-fixed-goalkeeper-id pelada) :is_goalkeeper 1})

                           (and (:fixed-goalkeepers pelada) (:away-fixed-goalkeeper-id pelada))
                           (assoc (:away-fixed-goalkeeper-id pelada)
                                  {:match_id match-id :team_id away :player_id (:away-fixed-goalkeeper-id pelada) :is_goalkeeper 1}))

            to-insert (vals final-lineup)]
        (if (empty? to-insert)
          0
          (try
            (count (sql/insert-multi! db :matchlineups to-insert))
            (catch Exception _ 0)))))))

(s/defn add-player :- s/Int
  [match-id :- s/Int team-id :- s/Int player-id :- s/Int db]
  (try
    (affected-rows-count (sql/insert! db :matchlineups {:match_id match-id :team_id team-id :player_id player-id}))
    (catch Exception _ 0)))

(s/defn remove-player :- s/Int
  [match-id :- s/Int team-id :- s/Int player-id :- s/Int db]
  (-> (sql/delete! db :matchlineups {:match_id match-id :team_id team-id :player_id player-id})
      affected-rows-count))

(s/defn replace-player :- s/Int
  [match-id :- s/Int team-id :- s/Int out-player-id :- s/Int in-player-id :- s/Int db]
  (let [;; Find if the outgoing player was a goalkeeper
        out-player (first (filter #(= out-player-id (:player_id %)) (list-by-match match-id db)))
        is-gk (and out-player (not= 0 (:is_goalkeeper out-player)))
        rm (remove-player match-id team-id out-player-id db)
        ;; If they were a goalkeeper, the incoming player should also be one
        ad (affected-rows-count (sql/insert! db :matchlineups {:match_id match-id :team_id team-id :player_id in-player-id :is_goalkeeper (if is-gk 1 0)}))]
    (+ rm ad)))

(s/defn list-match-lineups-by-pelada :- [s/Any]
  [pelada-id :- s/Int db]
  (->> (sql/query db ["SELECT ml.*
                        FROM MatchLineups ml
                        JOIN Matches m ON ml.match_id = m.id
                        WHERE m.pelada_id = ?" pelada-id])
       (map unqualify-row)
       vec))
