(ns api-peladaapp.helpers.exception
  (:require
   [api-peladaapp.helpers.responses :refer [bad-request forbidden not-found
                                            server-error too-many-requests
                                            unauthorized]]))

(defn- exception->map []
  {:error "server-error"
   :message "An unexpected error occurred. Please try again later."})

(defn api-exception-handler [e]
  (let [data (ex-data e)]
    (when-not data
      (println "[EXCEPTION]" (.getMessage e))
      (.printStackTrace e))
    (case (:type data)
      :already-exist       (bad-request (:message data))
      :not-found           (not-found (:message data))
      :invalid-credentials (unauthorized (:message data))
      :bad-request         (bad-request (:message data))
      :validation-error    (bad-request (:message data))
      :forbidden           (forbidden (:message data))
      :too-many-requests   (too-many-requests (:message data))
      (do
        (println "[SERVER ERROR]" (.getMessage e))
        (.printStackTrace e)
        (server-error (exception->map))))))
; NOTE: Full exception details are returned for easier debugging in dev.
; Do NOT keep this behavior in production.

