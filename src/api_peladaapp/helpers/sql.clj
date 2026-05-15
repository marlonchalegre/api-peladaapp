(ns api-peladaapp.helpers.sql
  (:refer-clojure :exclude [format])
  (:require [honey.sql :as sql]
            [next.jdbc.result-set :as rs]))

(def ^:private default-options
  {:dialect :ansi
   :quoted true
   :quoted-snake-case false})

(defn format
  "Formats a HoneySQL map into a [sql & params] vector using the ANSI dialect with strict quoting."
  ([query]
   (sql/format query default-options))
  ([query options]
   (sql/format query (merge default-options options))))

(defn affected-rows-count [result]
  (let [res (if (vector? result) (first result) result)]
    (or (:update-count res) (:next.jdbc/update-count res) (-> res vals first) 0)))

(def opts {:builder-fn rs/as-unqualified-lower-maps})
