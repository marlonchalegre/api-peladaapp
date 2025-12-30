(ns api-peladaapp.db.match-lineup
  (:require [api-peladaapp.db.match :as db.match]
            [api-peladaapp.db.team :as db.team]
            [next.jdbc.sql :as sql]
            [next.jdbc :as jdbc]
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
  (with-open [conn (jdbc/get-connection (db))]
    (sql/find-by-keys conn :matchlineups {:match_id match-id})))

(s/defn list-by-match-grouped :- {s/Int [s/Any]}
  [match-id db]
  (let [rows (list-by-match match-id db)]
    (reduce (fn [acc {:keys [matchlineups/team_id] :as row}]
              (update acc team_id (fnil conj []) (update-keys row (comp keyword name))))
            {} rows)))

(s/defn ensure-seeded :- s/Int
  "Seed lineup from team players for both sides if match has no lineup rows yet.
   Returns number of rows inserted (may be 0)."
  [match-id db]
  (with-open [conn (jdbc/get-connection (db))]
    (let [existing (list-by-match match-id db)]
      (if (seq existing)
        0
        (let [m (db.match/get-match match-id db)
              home (:home_team_id m)
              away (:away_team_id m)
              home-players (map :teamplayers/player_id (db.team/list-team-players home db))
              away-players (map :teamplayers/player_id (db.team/list-team-players away db))
              to-insert (concat (map (fn [pid] {:match_id match-id :team_id home :player_id pid}) home-players)
                                (map (fn [pid] {:match_id match-id :team_id away :player_id pid}) away-players))]
          (reduce (fn [acc row]
                    (+ acc
                       (try
                         (affected-rows-count (sql/insert! conn :matchlineups row))
                         (catch Exception _ 0))))
                  0
                  to-insert))))))

(s/defn add-player :- s/Int
  [match-id :- s/Int team-id :- s/Int player-id :- s/Int db]
  (try
    (with-open [conn (jdbc/get-connection (db))]
      (affected-rows-count (sql/insert! conn :matchlineups {:match_id match-id :team_id team-id :player_id player-id})))
    (catch Exception _ 0)) )

(s/defn remove-player :- s/Int
  [match-id :- s/Int team-id :- s/Int player-id :- s/Int db]
  (with-open [conn (jdbc/get-connection (db))]
    (-> (sql/delete! conn :matchlineups {:match_id match-id :team_id team-id :player_id player-id})
        affected-rows-count)))

(s/defn replace-player :- s/Int
  [match-id :- s/Int team-id :- s/Int out-player-id :- s/Int in-player-id :- s/Int db]
  (let [rm (remove-player match-id team-id out-player-id db)
        ad (add-player match-id team-id in-player-id db)]
    (+ rm ad)))

(s/defn list-match-lineups-by-pelada :- [s/Any]
  [pelada-id :- s/Int db]
  (with-open [conn (jdbc/get-connection (db))]
    (->> (sql/query conn ["SELECT ml.*
                        FROM MatchLineups ml
                        JOIN Matches m ON ml.match_id = m.id
                        WHERE m.pelada_id = ?" pelada-id])
         (map unqualify-row)
         vec)))
