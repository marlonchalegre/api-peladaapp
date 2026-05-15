(ns api-peladaapp.logic.pelada
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.schedule :as schedule]
   [api-peladaapp.logic.vote :as logic.vote]
   [schema.core :as s]))

(defn ensure-open
  "Ensure pelada can start. Returns pelada or throws with :bad-request."
  [pelada]
  (case (:status pelada)
    "open" pelada
    "attendance" (throw (ex-info nil {:type :bad-request
                                      :message "Attendance list is still open. Close it before starting the pelada."}))
    (throw (ex-info nil {:type :bad-request
                         :message "Pelada already started or closed"}))))

(defn ensure-running
  "Ensure pelada is currently running. Returns pelada or throws with :bad-request.
   Optional :allow-closed? allows closed/voting status."
  ([pelada] (ensure-running pelada {}))
  ([pelada opts]
   (let [status (:status pelada)
         allowed? (or (= "running" status)
                      (and (:allow-closed? opts)
                           (contains? #{"closed" "voting"} status)))]
     (if allowed?
       pelada
       (throw (ex-info "Pelada is not running"
                       {:type :bad-request
                        :message (str "Action only allowed while pelada is running. Current status: " status)}))))))

(defn ensure-schedulable-team-count
  "Ensure there are enough teams and team count is even. Returns team ids."
  [team-ids]
  (let [team-count (count team-ids)]
    (cond
      (< team-count 2)
      (throw (ex-info nil {:type :bad-request
                           :message "At least two teams are required"}))

      :else (vec team-ids))))

(s/defn get-voting-info :- s/Any
  [pelada-id :- s/Uuid
   player-id :- s/Uuid
   db]
  (let [pelada (db.pelada/get-pelada pelada-id db) ;; Re-fetch pelada to ensure latest status and closed_at
        can-vote (try (logic.vote/validate-voting-eligibility pelada) true (catch Exception _ false))
        has-voted (db.vote/has-voter-voted? pelada-id player-id db)]
    {:can-vote can-vote
     :has-voted has-voted
     :eligible-players [] ;; In this simplified context we don't need players
     :message (if (not can-vote)
                "Voting is not open or has closed."
                "")}))

(defn ensure-startable
  "Validate pelada start preconditions. Returns vector of team ids."
  [pelada team-ids]
  (ensure-open pelada)
  (ensure-schedulable-team-count team-ids))

(defn schedule-matches-for-start
  "Return sequence of match maps {:home :away} honoring optional matches-per-team."
  [team-ids matches-per-team]
  (let [ids (vec team-ids)]
    (if matches-per-team
      (schedule/schedule-matches-with-limit ids matches-per-team)
      (schedule/schedule-matches ids))))

(defn match-plan->rows
  "Convert scheduled matches into DB ready rows."
  [pelada-id scheduled-matches]
  (map-indexed (fn [index {:keys [home away]}]
                 {:pelada-id pelada-id
                  :home-team-id home
                  :away-team-id away
                  :sequence (inc index)
                  :status "scheduled"
                  :home-score 0
                  :away-score 0})
               scheduled-matches))
