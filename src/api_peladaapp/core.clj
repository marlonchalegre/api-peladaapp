(ns api-peladaapp.core
  (:require [api-peladaapp.components :as core.components]
            [com.stuartsierra.component :as component]))

(defn -main
  []
  (component/start (core.components/system {})))
