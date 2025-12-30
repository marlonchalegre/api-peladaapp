(ns api-peladaapp.helpers.pagination)

(defn- to-int [s default]
  (if (string? s)
    (try (Integer/parseInt s) (catch NumberFormatException _ default))
    default))

(defn parse-pagination-params [query-params]
  {:page (to-int (get query-params "page" "1") 1)
   :per-page (to-int (get query-params "per_page" "20") 20)})

(defn with-pagination-headers [data total-count page per-page]
  {:data data
   :headers {"X-Total" (str total-count)
             "X-Total-Pages" (str (long (Math/ceil (/ total-count (double per-page)))))
             "X-Per-Page" (str per-page)
             "X-Page" (str page)}})
