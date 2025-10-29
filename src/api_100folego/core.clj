(ns api-100folego.core
  (:require [api-100folego.components :as core.components]
            [com.stuartsierra.component :as component]))

(defn -main
  []
  (component/start (core.components/system {})))
