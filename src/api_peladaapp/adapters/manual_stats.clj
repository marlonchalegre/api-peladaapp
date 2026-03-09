(ns api-peladaapp.adapters.manual-stats
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.models.manual-stats :as models.manual-stats]
   [clojure.string :as str]
   [schema.core :as s]))

(s/defn db->model :- models.manual-stats/ManualStats
  [db-row]
  (when db-row
    (-> db-row
        misc/unamespace
        (update-keys (comp keyword #(str/replace % "_" "-") name)))))
