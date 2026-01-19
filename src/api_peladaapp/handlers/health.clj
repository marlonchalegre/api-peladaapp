(ns api-peladaapp.handlers.health
  (:require [ring.util.response :as response]))

(defn check [_]
  (response/response "OK"))
