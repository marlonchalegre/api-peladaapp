(ns api-peladaapp.logic.substitution)

(defn ensure-distinct-players
  [out-player-id in-player-id]
  (when (= out-player-id in-player-id)
    (throw (ex-info "Players must differ"
                    {:type :bad-request
                     :message "in and out cannot be the same"}))))

(defn ensure-players-belong
  [allowed player-ids]
  (let [missing (remove allowed player-ids)]
    (when (seq missing)
      (throw (ex-info "Players must belong to pelada teams"
                      {:type :bad-request
                       :message "Players must belong to pelada teams"
                       :missing (set missing)})))))

(defn validate-substitution
  "Ensure substitution respects domain rules. Returns original substitution map."
  [{:keys [out_player_id in_player_id] :as substitution}
   allowed-player-ids]
  (ensure-distinct-players out_player_id in_player_id)
  (ensure-players-belong allowed-player-ids [out_player_id in_player_id])
  substitution)
