(ns api-peladaapp.helpers.time
  (:require [clojure.string :as str])
  (:import (java.sql Timestamp)
           (java.time Instant LocalDateTime ZoneOffset)
           (java.util Date)))

(defn ->instant
  "Coerce a value returned from JDBC (Timestamp / Instant / LocalDateTime / Date / String)
   into a java.time.Instant. Returns nil on nil input. Throws on unsupported types."
  ^Instant [v]
  (cond
    (nil? v) nil
    (instance? Instant v) v
    (instance? Timestamp v) (.toInstant ^Timestamp v)
    (instance? LocalDateTime v) (.toInstant ^LocalDateTime v ZoneOffset/UTC)
    (instance? Date v) (.toInstant ^Date v)
    (string? v) (let [s (str/replace v \space \T)
                      s (if (or (str/ends-with? s "Z")
                                (re-find #"[+-]\d{2}:?\d{2}$" s))
                          s
                          (str s "Z"))]
                  (Instant/parse s))
    :else (throw (ex-info (str "Cannot coerce to Instant: " (class v))
                          {:value v :type (class v)}))))
