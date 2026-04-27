(ns api-peladaapp.handlers.internal
  (:require
   [api-peladaapp.helpers.responses :refer [ok]]
   [api-peladaapp.logic.scheduler :as scheduler]))

(defn trigger-scheduler [request]
  (let [db (:database request)]
    (scheduler/execute-tasks! db)
    (ok {:status "success" :message "Scheduler tasks triggered"})))

(defn- resolve-profiler [sym]
  (try
    (requiring-resolve sym)
    (catch Exception _ nil)))

(defn start-profiler [_]
  (if-let [start-fn (resolve-profiler 'clj-async-profiler.core/start)]
    (do
      (start-fn)
      (ok {:status "success" :message "Profiler started"}))
    (ok {:status "error" :message "Profiler not available in this environment"})))

(defn stop-profiler [_]
  (if-let [stop-fn (resolve-profiler 'clj-async-profiler.core/stop)]
    (let [file (stop-fn)]
      (ok {:status "success" :message "Profiler stopped" :file (str file)}))
    (ok {:status "error" :message "Profiler not available in this environment"})))

(defn serve-profiler-results [_]
  (if-let [serve-fn (resolve-profiler 'clj-async-profiler.core/serve-ui)]
    (do
      (serve-fn 8081)
      (ok {:status "success" :message "Profiler files served on port 8081"}))
    (ok {:status "error" :message "Profiler not available in this environment"})))
