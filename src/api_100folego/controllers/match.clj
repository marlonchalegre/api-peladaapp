(ns api-100folego.controllers.match
  (:require [api-100folego.db.match :as db.match]
            [api-100folego.db.match-event :as db.match-event]
            [api-100folego.db.match-lineup :as db.match-lineup]
            [schema.core :as s]))

(s/defn list-matches :- [s/Any]
  [pelada-id :- s/Int db]
  (db.match/list-matches-by-pelada pelada-id db))

(s/defn update-score :- s/Int
  [match-id :- s/Int {:keys [home_score away_score status]} db]
  (when (or (and (some? home_score) (neg? home_score))
            (and (some? away_score) (neg? away_score)))
    (throw (ex-info "Negative score not allowed" {:type :bad-request :message "Placar não pode ser negativo" :home_score home_score :away_score away_score})))
  (db.match/update-score match-id {:home_score home_score :away_score away_score :status status} db))

(def ^:private allowed-event-types #{"assist" "goal" "own_goal"})

(s/defn create-event :- s/Int
  [match-id :- s/Int {:keys [player_id event_type]} db]
  (if-not (allowed-event-types (str event_type))
    (throw (ex-info "Invalid event type" {:type :bad-request :event_type event_type}))
    (db.match-event/insert-event match-id player_id (str event_type) db)))

(s/defn list-events-by-pelada :- [s/Any]
  [pelada-id :- s/Int db]
  (db.match-event/list-events-by-pelada pelada-id db))

(s/defn delete-last-event :- s/Int
  [match-id :- s/Int {:keys [player_id event_type]} db]
  (db.match-event/delete-last-event match-id player_id (str event_type) db))

(s/defn list-player-stats-by-pelada :- [s/Any]
  [pelada-id :- s/Int db]
  (db.match-event/list-player-stats-by-pelada pelada-id db))

;; Match lineups (per-match players)
(s/defn list-lineups-by-match :- {s/Int [s/Any]}
  [match-id :- s/Int db]
  (do (db.match-lineup/ensure-seeded match-id db)
      (db.match-lineup/list-by-match-grouped match-id db)))

(s/defn add-lineup-player :- s/Int
  [match-id :- s/Int {:keys [team_id player_id]} db]
  (db.match-lineup/add-player match-id (int team_id) (int player_id) db))

(s/defn remove-lineup-player :- s/Int
  [match-id :- s/Int {:keys [team_id player_id]} db]
  (db.match-lineup/remove-player match-id (int team_id) (int player_id) db))

(s/defn replace-lineup-player :- s/Int
  [match-id :- s/Int {:keys [team_id out_player_id in_player_id]} db]
  (db.match-lineup/replace-player match-id (int team_id) (int out_player_id) (int in_player_id) db))
