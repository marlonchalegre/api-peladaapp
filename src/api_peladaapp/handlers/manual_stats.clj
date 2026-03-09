(ns api-peladaapp.handlers.manual-stats
  (:require
   [api-peladaapp.controllers.manual-stats :as controller.manual-stats]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.responses :refer [ok]]
   [clojure.string :as str]))

(defn- sanitize-keys [m]
  (into {} (map (fn [[k v]] [(keyword (str/replace (name k) "_" "-")) v]) m)))

(defn upsert-manual-stats [request]
  (try
    (let [db (:database request)
          user-id (get-in request [:identity :id])
          organization-id (Integer/parseInt (str (get-in request [:params :id])))
          stats (map sanitize-keys (:body request))]
      (ok {:updated (controller.manual-stats/upsert-manual-stats user-id organization-id stats db)}))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn list-manual-stats [request]
  (try
    (let [db (:database request)
          organization-id (Integer/parseInt (str (get-in request [:params :id])))
          year (Integer/parseInt (str (get-in request [:query-params "year"])))]
      (ok (controller.manual-stats/list-manual-stats organization-id year db)))
    (catch Exception e
      (exception/api-exception-handler e))))
