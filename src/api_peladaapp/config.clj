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
    (get data key)))
