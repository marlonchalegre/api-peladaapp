(defproject api-peladaapp "0.1.0-SNAPSHOT"
  :description "Clojure HTTP API for PeladaApp"
  :url "http://example.com/FIXME"
  :license {:name "MIT"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/data.json "2.5.2"]
                 [org.clojure/math.combinatorics "0.3.0"]
                 [ring/ring-core "1.11.0"]
                 [ring/ring-jetty-adapter "1.11.0"]
                 [ring/ring-defaults "0.4.0"]
                 [ring/ring-json "0.5.1"]
                 [ring-cors "0.1.13"]
                 [buddy/buddy-core "1.12.0-430"]
                 [buddy/buddy-sign "3.6.1-359"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-hashers "2.0.167"]
                 [metosin/ring-http-response "0.9.4"]
                 [compojure "1.7.2"]
                 [com.github.seancorfield/next.jdbc "1.3.1093"]
                 [medley/medley "1.4.0"]
                 [org.postgresql/postgresql "42.6.0"]
                 [com.github.seancorfield/honeysql "2.6.1147"]
                 [prismatic/schema "1.4.1"]
                 [clj-http "3.13.0"]
                 [com.draines/postal "2.0.4"]
                 [com.stuartsierra/component "1.1.0"]
                 [hikari-cp "3.0.1"]
                 [migratus "1.6.3"]
                 [org.clojure/tools.logging "1.3.0"]
                 [ch.qos.logback/logback-classic "1.5.16"]
                 [org.slf4j/slf4j-api "2.0.16"]]
  :main ^:skip-aot api-peladaapp.core
  :plugins [[lein-ring "0.12.6"]
            [lein-ancient "1.0.0-RC3"]]
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [org.clojure/tools.namespace "1.5.1"]
                                  [com.clojure-goes-fast/clj-async-profiler "1.7.0"]
                                  [com.stuartsierra/component.repl "0.2.0"]
                                  [clj-kondo "2026.04.15"]
                                  [com.github.clojure-lsp/clojure-lsp "2026.05.05-12.58.26"]]
                   :plugins [[com.github.clojure-lsp/lein-clojure-lsp "2.0.15"]
                             [com.github.clj-kondo/lein-clj-kondo "2026.01.19"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns dev}}
             :test {:dependencies [[ring/ring-mock "0.4.0"]]}
             :uberjar {:aot :all}}
  :test-paths ["test" "test/unit" "test/integration"]
  :aliases {"clj-kondo" ["clj-kondo" "--lint" "src" "test"]
            "format" ["clojure-lsp" "format" "--filenames" "src,test" "--dry"]
            "format-fix" ["clojure-lsp" "format" "--filenames" "src,test"]
            "clean-ns" ["clojure-lsp" "clean-ns" "--filenames" "src,test" "--dry"]
            "clean-ns-fix" ["clojure-lsp" "clean-ns" "--filenames" "src,test"]
            "lint" ["do" ["clj-kondo"] ["format"] ["clean-ns"]]
            "lint-fix" ["do" ["format-fix"] ["clean-ns-fix"]]}
  :ring {:handler api-peladaapp.server/app
         :port 8000
         :reload-paths ["src"]}
  :target-path "target/%s")
