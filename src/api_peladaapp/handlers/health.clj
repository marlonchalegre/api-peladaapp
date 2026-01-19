(ns api-peladaapp.handlers.health
  (:require
   [ring.util.response :as response]))

(defn check [_]
  (response/response {:status "OK"
                      :version (or (System/getenv "APP_VERSION") "development")}))
