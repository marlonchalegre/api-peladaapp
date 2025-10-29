(ns api-peladaapp.helpers.exception
  (:require
   [api-peladaapp.helpers.responses :refer [bad-request server-error not-found]]))

(defn- exception->map [^Throwable e]
  (let [sw (java.io.StringWriter.)
        pw (java.io.PrintWriter. sw)]
    (.printStackTrace e pw)
    {:error "exception"
     :message (.getMessage e)
     :data (ex-data e)
     :stacktrace (.toString sw)}))

(defn api-exception-handler [e]
  (let [data (ex-data e)]
    (case (:type data)
      :already-exist       (bad-request (:message data))
      :not-found           (not-found (:message data))
      :invalid-credentials (bad-request (:message data))
      :bad-request         (bad-request (:message data))
      (server-error (exception->map e)))))
; NOTE: Full exception details are returned for easier debugging in dev.
; Do NOT keep this behavior in production.

