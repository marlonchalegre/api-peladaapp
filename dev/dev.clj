(ns dev
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application.

  Call `(reset)` to reload modified code and (re)start the system.

  The system under development is `system`, referred from
  `com.stuartsierra.component.repl/system`.

  See also https://github.com/stuartsierra/component.repl"
  (:require
   [api-peladaapp.components :as core.components]
   [api-peladaapp.core]
   [api-peladaapp.server :as server]
   [clojure.tools.namespace.repl :as repl]
   [com.stuartsierra.component :as component]
   [com.stuartsierra.component.repl :refer [set-init]]))

;; Do not try to load source code from 'resources' directory
(repl/set-refresh-dirs "dev" "src" "test")

(defn dev-system
  "Constructs a system map suitable for interactive development."
  []
  (component/system-map
   :database (core.components/new-database)
   :app      (core.components/new-app server/app)
   :server   (core.components/new-web-server)))

(set-init (fn [_] (dev-system)))
