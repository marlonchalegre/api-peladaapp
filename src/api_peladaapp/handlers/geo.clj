(ns api-peladaapp.handlers.geo
  (:require
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [ok]]
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(defonce ^:private cache (atom {}))
(defonce ^:private last-request-time (atom 0))
(defonce ^:private rate-limit-lock (Object.))

(def ^:private cache-ttl-ms (* 24 60 60 1000))
(def ^:private max-cache-entries 500)

(defn- clean-expired-cache! [current-cache now]
  (into {}
        (filter (fn [[_ v]] (> (+ (:timestamp v) cache-ttl-ms) now)))
        current-cache))

(defn- get-from-cache [query]
  (let [now (System/currentTimeMillis)
        entry (get @cache query)]
    (when (and entry (> (+ (:timestamp entry) cache-ttl-ms) now))
      (:data entry))))

(defn- put-in-cache! [query data]
  (let [now (System/currentTimeMillis)]
    (swap! cache (fn [c]
                   (let [cleaned (if (> (count c) max-cache-entries)
                                   (clean-expired-cache! c now)
                                   c)]
                     (assoc cleaned query {:timestamp now :data data}))))))

(defn- throttle! []
  (locking rate-limit-lock
    (let [now (System/currentTimeMillis)
          elapsed (- now @last-request-time)]
      (when (< elapsed 1000)
        (Thread/sleep (- 1000 elapsed)))
      (reset! last-request-time (System/currentTimeMillis)))))

(defn- format-result [item]
  {:place_id (:place_id item)
   :display_name (:display_name item)
   :name (:name item)
   :lat (:lat item)
   :lon (:lon item)})

(defn- fetch-from-nominatim [query]
  (let [user-agent (or (System/getenv "NOMINATIM_USER_AGENT")
                       "PeladaApp/1.0 (https://github.com/marlonchalegre/app-pelada-orchestrator; support@peladaapp.com)")]
    (throttle!)
    (try
      (let [response (http/get "https://nominatim.openstreetmap.org/search"
                               {:query-params {"format" "json"
                                               "q" query
                                               "addressdetails" "1"
                                               "limit" "5"}
                                :headers {"User-Agent" user-agent
                                          "Accept" "application/json"
                                          "Accept-Language" "pt-BR,pt;q=0.9,en;q=0.8"}
                                :socket-timeout 5000
                                :conn-timeout 5000
                                :as :text})
            parsed (json/read-str (:body response) :key-fn keyword)]
        (mapv format-result parsed))
      (catch Exception e
        (log/warn e (str "Nominatim search failed for query: " query))
        []))))

(defn search [request]
  (try
    (let [params (:params request)
          query-param (or (:q params) (get params "q"))
          query (some-> query-param str/trim)]
      (if (or (nil? query) (< (count query) 3))
        (ok [])
        (let [norm-query (str/lower-case query)]
          (if-let [cached (get-from-cache norm-query)]
            (ok cached)
            (let [results (fetch-from-nominatim query)]
              (when (seq results)
                (put-in-cache! norm-query results))
              (ok results))))))
    (catch Exception e
      (exception/api-exception-handler e))))
