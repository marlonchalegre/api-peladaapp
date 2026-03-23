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
                            :updated_at (:updated_at r))))
