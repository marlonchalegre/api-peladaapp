(ns api-peladaapp.config
  (:require
   [clojure.data.json :as json]))

(def data
  (json/read-str (slurp "resources/config.json")
                 :key-fn keyword))

(defn get-key
  [key]
  (if (= key :jwt-secret)
    (or (System/getenv "PELADA_API_SECURITY_SIGNING_KEY")
        (get data key nil))
    (get data key)))
