(ns api-peladaapp.adapters.attendance
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [medley.core :as medley.core]
   [schema.core :as s]))

(s/defn db->response
  [row]
  (let [r (misc/unamespace row)]
    (medley.core/assoc-some {}
                            :player_id (or (:player_id r) (:player-id r))
                            :status (:status r)
                            :updated_at (:updated_at r)
                            :voting-enabled (if (contains? r :voting_enabled)
                                              (if (boolean? (:voting_enabled r)) (:voting_enabled r) (= 1 (:voting_enabled r)))
                                              true))))

(defn payload->attendance [payload]
  (when payload
    (let [p (misc/unamespace payload)]
      (medley.core/assoc-some {}
                              :pelada-id (misc/as-uuid (:id p))
                              :player-id (misc/as-uuid (:player_id p))
                              :player-ids (when (:player_ids p) (map misc/as-uuid (:player_ids p)))
                              :status (:status p)
                              :enabled (:enabled p)))))
