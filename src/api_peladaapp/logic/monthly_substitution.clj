(ns api-peladaapp.logic.monthly-substitution
  (:require
   [api-peladaapp.db.monthly-substitution :as db.monthly-sub]
   [api-peladaapp.db.player :as db.player]
   [next.jdbc :as jdbc]
   [schema.core :as s]))

(s/defn substitute-player!
  [org-id permanent-player-id temporary-player-id start-date db]
  (jdbc/with-transaction [tx db]
    (let [permanent-player (db.player/get-player permanent-player-id tx)]

      (when-not (= (:member-type permanent-player) "mensalista")
        (throw (ex-info "Permanent player must be a mensalista"
                        {:type :bad-request :message "Permanent player must be a mensalista"})))

      (when (db.monthly-sub/get-active-substitution-by-permanent-player permanent-player-id tx)
        (throw (ex-info "Permanent player already has an active substitute"
                        {:type :bad-request :message "Permanent player already has an active substitute"})))

      (when (db.monthly-sub/get-active-substitution-by-temporary-player temporary-player-id tx)
        (throw (ex-info "Temporary player is already substituting someone else"
                        {:type :bad-request :message "Temporary player is already substituting someone else"})))

      ;; Create substitution record
      (db.monthly-sub/create-substitution!
       {:organization_id org-id
        :permanent_player_id permanent-player-id
        :temporary_player_id temporary-player-id
        :start_date start-date
        :active true}
       tx)

      ;; Update statuses
      (db.player/update-player permanent-player-id {:member-type "diarista_temporario"} tx)
      (db.player/update-player temporary-player-id {:member-type "mensalista_temporario"} tx)

      {:status :success})))

(s/defn end-substitution!
  [sub-id end-date db]
  (jdbc/with-transaction [tx db]
    (let [sub (db.monthly-sub/get-substitution-by-id sub-id tx)]
      (when-not sub
        (throw (ex-info "Substitution not found" {:type :not-found})))

      (when (not (:active sub))
        (throw (ex-info "Substitution already ended" {:type :bad-request})))

      ;; End substitution record
      (db.monthly-sub/end-substitution! sub-id end-date tx)

      ;; Revert statuses
      ;; Permanent goes back to mensalista
      (db.player/update-player (:permanent_player_id sub) {:member-type "mensalista"} tx)
      ;; Temporary goes back to diarista (assuming default)
      (db.player/update-player (:temporary_player_id sub) {:member-type "diarista"} tx)

      {:status :success})))
