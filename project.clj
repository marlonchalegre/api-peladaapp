(defproject api-peladaapp "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "MIT"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/data.json "2.5.1"]
                 [ring/ring "1.13.0"]
                 [buddy/buddy-core "1.12.0-430"]
                 [buddy/buddy-sign "3.6.1-359"]
                 [buddy/buddy-auth "3.0.323"]
                 [buddy/buddy-hashers "1.4.0"]
                 [ring/ring-json "0.5.1"]
                 [compojure "1.7.2"]
                 [com.github.seancorfield/next.jdbc "1.3.1086"]
                 [medley/medley "1.4.0"]
                 [org.xerial/sqlite-jdbc  "3.49.0.0"]
                 [com.dbeaver.jdbc/com.dbeaver.jdbc.driver.libsql "1.0.5"]
                 [migratus "1.6.4"]
                 [prismatic/schema "1.4.1"]
                 [com.stuartsierra/component "1.1.0"]
                 [com.zaxxer/HikariCP  "6.2.1"]
                 [org.slf4j/slf4j-api "2.0.16"]
                 [org.slf4j/slf4j-simple "2.0.16"]
                 [org.clojure/math.combinatorics "0.3.0"]
                 [jarohen/chime "0.3.3"]
                 [clj-http "3.13.0"]
                 [com.draines/postal "2.0.5"]]
  :plugins [[lein-ancient "1.0.0-RC3"]
            [lein-ring "0.12.6"]
            [migratus-lein "0.7.3"]]
  :repl-options {:init-ns api-peladaapp.core}
  :test-paths ["test" "test/unit" "test/integration"]
  :main api-peladaapp.core
  :migratus {:store :database
             ;; Use classpath path for migrations
             :migration-dir "migrations"
             :init-script  "migrations/init.sql"
             :db {:dbtype "sqlite"
                  :dbname "peladaapp.db"}}
  :profiles {:dev {:plugins      [[com.github.clojure-lsp/lein-clojure-lsp "1.4.9"]
                                  [com.github.clj-kondo/lein-clj-kondo "0.2.5"]]
                   :dependencies [[com.stuartsierra/component.repl "1.0.0"]
                                  [prismatic/schema-generators "0.1.5"]
                                  [org.clojure/tools.namespace "1.4.4"]
                                  [ring/ring-devel "1.13.0"]
                                  [com.clojure-goes-fast/clj-async-profiler "1.6.1"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns dev}}
            :test {:dependencies [[ring/ring-mock "0.4.0"]]}}
  :ring {:handler api-peladaapp.server/app
         :port 8000
         :reload-paths ["src"]}
  :target-path "target/%s")
