(ns api-peladaapp.helpers.misc)

(defn unamespace
  "Remove the namespace from a map of keywords"
  [data]
  (if (empty? data)
    data
    (update-keys data (fn [k] (if (instance? clojure.lang.Named k) (keyword (name k)) k)))))
