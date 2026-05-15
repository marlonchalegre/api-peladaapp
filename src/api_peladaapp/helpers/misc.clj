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

(defn as-uuid
  "Safely convert a string to a UUID. If it's already a UUID, return it.
   Returns nil if input is nil or invalid."
  [x]
  (cond
    (instance? java.util.UUID x) x
    (string? x) (parse-uuid x)
    :else nil))
