(ns api-100folego.handlers.match
  (:require [api-100folego.controllers.match :as match-controller]
            [api-100folego.helpers.exception :as exception]
            [api-100folego.helpers.responses :refer [ok updated]]
            [clojure.string :as cstr]))

(defn list-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (get-in request [:params :pelada_id])]
         (ok (match-controller/list-matches pelada-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-events-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (clojure.core/str (get-in request [:params :pelada_id])))]
         (ok (match-controller/list-events-by-pelada pelada-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn list-player-stats-by-pelada [request]
  (try (let [db (:database request)
             pelada-id (Integer/parseInt (clojure.core/str (get-in request [:params :pelada_id])))]
         (ok (match-controller/list-player-stats-by-pelada pelada-id db)))
       (catch Exception e (exception/api-exception-handler e))))

(defn update-score [request]
  (try (let [db (:database request)
             id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
             {:keys [home_score away_score status]} (:body request)]
         (if (and (nil? home_score) (nil? away_score) (nil? status))
           {:status 400 :body {:error "bad-request" :message "Provide at least one of home_score, away_score or status"}}
           (updated (match-controller/update-score id {:home_score home_score :away_score away_score :status status} db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn create-event [request]
  (try (let [db (:database request)
             id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
             {:keys [player_id event_type]} (:body request)
             et (some-> (clojure.core/str event_type) cstr/lower-case)
             ;; accept PT aliases
             et* (case et
                   "assistencia" "assist"
                   "gol" "goal"
                   "gol_contra" "own_goal"
                   "gol-contra" "own_goal"
                   et)]
         (cond
           (nil? player_id) {:status 400 :body {:error "bad-request" :message "player_id is required"}}
           (nil? et*) {:status 400 :body {:error "bad-request" :message "event_type is required"}}
           :else (updated (match-controller/create-event id {:player_id player_id :event_type et*} db))))
       (catch Exception e (exception/api-exception-handler e))))

(defn delete-event [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          {:keys [player_id event_type]} (:body request)
          et (some-> (clojure.core/str event_type) cstr/lower-case)
          et* (case et
                "assistencia" "assist"
                "gol" "goal"
                "gol_contra" "own_goal"
                "gol-contra" "own_goal"
                et)]
      (cond
        (nil? player_id) {:status 400 :body {:error "bad-request" :message "player_id is required"}}
        (nil? et*) {:status 400 :body {:error "bad-request" :message "event_type is required"}}
        :else (updated (match-controller/delete-last-event id {:player_id player_id :event_type et*} db))))
    (catch Exception e
      (exception/api-exception-handler e))))

;; Lineups (per-match players)
(defn list-lineups [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))]
      (ok (match-controller/list-lineups-by-match id db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn add-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          {:keys [team_id player_id]} (:body request)]
      (updated (match-controller/add-lineup-player id {:team_id team_id :player_id player_id} db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn remove-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          {:keys [team_id player_id]} (:body request)]
      (updated (match-controller/remove-lineup-player id {:team_id team_id :player_id player_id} db)))
    (catch Exception e (exception/api-exception-handler e))))

(defn replace-lineup-player [request]
  (try
    (let [db (:database request)
          id (Integer/parseInt (clojure.core/str (get-in request [:params :id])))
          {:keys [team_id out_player_id in_player_id]} (:body request)]
      (updated (match-controller/replace-lineup-player id {:team_id team_id :out_player_id out_player_id :in_player_id in_player_id} db)))
    (catch Exception e (exception/api-exception-handler e))))
