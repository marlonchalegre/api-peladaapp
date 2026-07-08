(ns api-peladaapp.handlers.manual-stats
  (:require
   [api-peladaapp.adapters.manual-stats :as adapter.manual-stats]
   [api-peladaapp.controllers.manual-stats :as controller.manual-stats]
   [api-peladaapp.helpers.exception :as exception]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.responses :refer [ok]]
   [api-peladaapp.logic.authorization :as auth]))

(defn upsert-manual-stats [request]
  (try
    (let [db (:database request)
          user-id (auth/get-user-id-from-request request)
          organization-id (misc/as-uuid (get-in request [:params :id]))
          stats (map adapter.manual-stats/payload->manual-stats (:body request))]
      (ok {:updated (controller.manual-stats/upsert-manual-stats user-id organization-id stats db)}))
    (catch Exception e
      (exception/api-exception-handler e))))

(defn list-manual-stats [request]
  (try
    (let [db (:database request)
          organization-id (misc/as-uuid (get-in request [:params :id]))
          year-str (get-in request [:query-params "year"])
          year (try
                 (Integer/parseInt (str year-str))
                 (catch NumberFormatException _
                   (throw (ex-info "Invalid year parameter" {:type :bad-request :message "Invalid year parameter"}))))]
      (ok (controller.manual-stats/list-manual-stats organization-id year db)))
    (catch Exception e
      (exception/api-exception-handler e))))
