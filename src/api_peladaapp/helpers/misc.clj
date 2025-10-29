(ns api-peladaapp.helpers.misc)

(defn unamespace
  "Remove the namespace from a map of keywords"
  [data]
  (update-keys data (comp keyword name)))
