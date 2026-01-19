(ns api-peladaapp.core
  (:gen-class)
  (:require
   [api-peladaapp.components :as core.components]
   [com.stuartsierra.component :as component]))

(defn -main
  []
  (component/start (core.components/system {})))
