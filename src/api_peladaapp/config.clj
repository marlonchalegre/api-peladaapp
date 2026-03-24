(ns api-peladaapp.config
  (:require
   [clojure.data.json :as json]))

(def data
  (json/read-str (slurp "resources/config.json")
                 :key-fn keyword))

(defn get-key
  [key]
  (case key
    :jwt-secret (or (System/getenv "PELADA_API_SECURITY_SIGNING_KEY")
                    (get data key nil))
    :waha-api-key (System/getenv "WAHA_API_KEY")
    :smtp-host (or (System/getenv "SMTP_HOST") (get data key "smtp.gmail.com"))
    :smtp-port (let [port (or (System/getenv "SMTP_PORT") (get data key 587))]
                 (if (string? port) (Integer/parseInt port) port))
    :smtp-user (or (System/getenv "SMTP_USER") (get data key))
    :smtp-pass (or (System/getenv "SMTP_PASS") (get data key))
    (get data key)))
