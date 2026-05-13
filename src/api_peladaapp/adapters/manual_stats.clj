(ns api-peladaapp.adapters.manual-stats
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.manual-stats :as models.manual-stats]
   [clojure.string :as str]
   [medley.core :as medley.core]
   [schema.core :as s]))

(s/defn db->model :- models.manual-stats/ManualStats
  [db-row]
  (when db-row
    (-> db-row
        misc/unamespace
        (update-keys (comp keyword #(str/replace % "_" "-") name)))))

(defn payload->manual-stats [payload]
  (when payload
    (let [p (misc/unamespace payload)]
      (medley.core/assoc-some {}
                              :organization-id (misc/as-uuid (or (:organization_id p) (:organization-id p) (:id p)))
                              :player-id (misc/as-uuid (or (:player_id p) (:player-id p)))
                              :year (:year p)
                              :goals (:goals p)
                              :assists (:assists p)
                              :own-goals (or (:own_goals p) (:own-goals p))
                              :mvps (:mvps p)
                              :yellow-cards (or (:yellow_cards p) (:yellow-cards p))
                              :red-cards (or (:red_cards p) (:red-cards p))))))
