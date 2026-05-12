(ns api-peladaapp.helpers.sql
  (:refer-clojure :exclude [format])
  (:require [honey.sql :as sql]))

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
