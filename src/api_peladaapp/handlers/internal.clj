(ns api-peladaapp.handlers.internal
  (:require
   [api-peladaapp.helpers.responses :refer [ok]]
   [api-peladaapp.logic.scheduler :as scheduler]
   [clj-async-profiler.core :as profiler]))

(defn trigger-scheduler [request]
  (let [db (:database request)]
    (scheduler/execute-tasks! db)
    (ok {:status "success" :message "Scheduler tasks triggered"})))

(defn start-profiler [_]
  (profiler/start)
  (ok {:status "success" :message "Profiler started"}))

(defn stop-profiler [_]
  (let [file (profiler/stop)]
    (ok {:status "success" :message "Profiler stopped" :file (str file)})))

(defn serve-profiler-results [_]
  (profiler/serve-ui 8081)
  (ok {:status "success" :message "Profiler files served on port 8081"}))
