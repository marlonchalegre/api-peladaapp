(ns api-peladaapp.handlers.internal
  (:require
   [api-peladaapp.helpers.responses :refer [ok]]
   [api-peladaapp.logic.scheduler :as scheduler]))

(defn trigger-scheduler [request]
  (let [db (:database request)]
    (scheduler/execute-tasks! db)
    (ok {:status "success" :message "Scheduler tasks triggered"})))
