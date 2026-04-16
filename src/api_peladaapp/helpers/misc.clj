(ns api-peladaapp.helpers.misc)

(defn unamespace
  "Remove the namespace from a map of keywords"
  [data]
  (if (empty? data)
    data
    (update-keys data (fn [k] (if (instance? clojure.lang.Named k) (keyword (name k)) k)))))

(defn rename-key
  "Rename a key in a map"
  [m old-k new-k]
  (if (contains? m old-k)
    (-> m
        (assoc new-k (get m old-k))
        (dissoc old-k))
    m))
