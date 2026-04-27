(ns api-peladaapp.handlers.health
  (:require
   [api-peladaapp.logic.waha :as waha]
   [ring.util.response :as response]))

(defn check [_]
  (response/response {:status "OK"
                      :version (or (System/getenv "APP_VERSION") "development")}))

(defn waha-healthcheck [_]
  (let [result (waha/healthcheck)]
    (if (= (:status result) "UP")
      (response/response result)
      (-> (response/response result)
          (response/status 503)))))

(defn waha-resume [_]
  (let [session-name (or (System/getenv "WAHA_DEFAULT_SESSION") "default")
        result (waha/resume-session session-name)]
    (if (= (:status result) "success")
      (response/response result)
      ;; If resume fails, try to restart/recreate
      (let [restart-result (waha/restart-session session-name)]
        (if (= (:status restart-result) "success")
          (response/response restart-result)
          (-> (response/response restart-result)
              (response/status 500)))))))

(defn waha-restart [_]
  (let [session-name (or (System/getenv "WAHA_DEFAULT_SESSION") "default")
        result (waha/restart-session session-name)]
    (if (= (:status result) "success")
      (response/response result)
      (-> (response/response result)
          (response/status 500)))))
