(ns api-peladaapp.logic.support-lineup
  (:require
   [clojure.set :as set]))

(defn eligible-resting-players
  "Returns vector of player IDs who belong to resting teams and are confirmed."
  [resting-team-ids team-players confirmed-player-ids]
  (let [resting-set (set resting-team-ids)
        confirmed-set (set confirmed-player-ids)
        candidates (->> team-players
                        (filter (fn [tp]
                                  (let [tid (or (:team-id tp) (:team_id tp))]
                                    (contains? resting-set tid))))
                        (map (fn [tp] (or (:player-id tp) (:player_id tp))))
                        distinct)
        filtered-confirmed (when (seq confirmed-set)
                             (filter #(contains? confirmed-set %) candidates))
        confirmed-candidates (if (seq filtered-confirmed)
                               filtered-confirmed
                               candidates)]
    (vec confirmed-candidates)))

(defn pick-support-pair
  "Picks 2 distinct player IDs from candidates prioritizing lowest duty count in duty-count-map.
   Returns [camera-player-id stats-player-id]."
  [candidates duty-count-map]
  (cond
    (empty? candidates)
    [nil nil]

    (= 1 (count candidates))
    [(first candidates) nil]

    :else
    (let [grouped (group-by #(get duty-count-map % 0) candidates)
          sorted-counts (sort (keys grouped))
          shuffled-pool (mapcat #(shuffle (get grouped %)) sorted-counts)]
      [(first shuffled-pool) (second shuffled-pool)])))

(defn- match-resting-candidates
  [match all-team-set team-players confirmed-player-ids]
  (let [home-id (or (:home-team-id match) (:home_team_id match) (:home match))
        away-id (or (:away-team-id match) (:away_team_id match) (:away match))
        playing-set #{home-id away-id}
        resting-teams (vec (set/difference all-team-set playing-set))]
    (eligible-resting-players resting-teams team-players confirmed-player-ids)))

(defn generate-support-lineups
  "Given matches (ordered by sequence), all team-ids, team-players, and confirmed-player-ids,
   generates support lineup assignments for each match with fair round-robin distribution.
   Returns a vector of maps with :support-camera-player-id and :support-stats-player-id assigned."
  [matches all-team-ids team-players confirmed-player-ids]
  (let [all-team-set (set all-team-ids)]
    (loop [remaining matches
           duty-counts {}
           results []]
      (if-let [m (first remaining)]
        (let [candidates (match-resting-candidates m all-team-set team-players confirmed-player-ids)
              [camera-id stats-id] (pick-support-pair candidates duty-counts)
              new-duty-counts (cond-> duty-counts
                                camera-id (update camera-id (fnil inc 0))
                                stats-id (update stats-id (fnil inc 0)))
              res (assoc m
                         :support-camera-player-id camera-id
                         :support-stats-player-id stats-id)]
          (recur (rest remaining) new-duty-counts (conj results res)))
        results))))

(defn reroll-single-match
  "Re-rolls support lineup for target-match using historical duty counts from all other matches."
  [target-match other-matches all-team-ids team-players confirmed-player-ids]
  (let [all-team-set (set all-team-ids)
        candidates (match-resting-candidates target-match all-team-set team-players confirmed-player-ids)
        duty-counts (reduce (fn [acc m]
                              (let [c (or (:support-camera-player-id m) (:support_camera_player_id m))
                                    s (or (:support-stats-player-id m) (:support_stats_player_id m))]
                                (cond-> acc
                                  c (update c (fnil inc 0))
                                  s (update s (fnil inc 0)))))
                            {}
                            other-matches)
        [camera-id stats-id] (pick-support-pair candidates duty-counts)]
    (assoc target-match
           :support-camera-player-id camera-id
           :support-stats-player-id stats-id)))

(defn calculate-duty-stats
  "Computes duty statistics for all players across matches."
  [matches]
  (reduce (fn [acc m]
            (let [c (or (:support-camera-player-id m) (:support_camera_player_id m))
                  s (or (:support-stats-player-id m) (:support_stats_player_id m))]
              (cond-> acc
                c (-> (update-in [c :camera-count] (fnil inc 0))
                      (update-in [c :total] (fnil inc 0)))
                s (-> (update-in [s :stats-count] (fnil inc 0))
                      (update-in [s :total] (fnil inc 0))))))
          {}
          matches))
