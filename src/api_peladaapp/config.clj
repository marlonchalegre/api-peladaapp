(ns api-peladaapp.config
  (:require
   [clojure.data.json :as json]))

(def data
  (json/read-str (slurp "resources/config.json")
                 :key-fn keyword))

(defn get-key
  [key]
  (if (= key :jwt-secret)
    (or (System/getenv "JWT_SECRET")
        (get data key nil))
    (get data key)))
