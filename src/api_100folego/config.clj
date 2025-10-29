(ns api-100folego.config 
  (:require
   [clojure.data.json :as json]))

(def data
  (json/read-str (slurp "resources/config.json")
                 :key-fn keyword))

(defn get-key
  [key]
  (get data key))
