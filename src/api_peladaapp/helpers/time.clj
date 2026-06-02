(ns api-peladaapp.helpers.time
  (:require [clojure.string :as str])
  (:import (java.sql Timestamp)
           (java.time
            Instant
            LocalDateTime
            OffsetDateTime
            ZoneId
            ZoneOffset
            ZonedDateTime)
           (java.time.format DateTimeFormatter)
           (java.util Date)))

(def ^:private utc-formatter
  (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSS")
      (.withZone (ZoneId/of "UTC"))))

(defn ->instant
  "Coerce a value returned from JDBC (Timestamp / Instant / LocalDateTime / Date / String)
   into a java.time.Instant. Returns nil on nil input. Throws on unsupported types."
  ^Instant [v]
  (cond
    (nil? v) nil
    (instance? Instant v) v
    (instance? Timestamp v) (.toInstant ^Timestamp v)
    (instance? LocalDateTime v) (.toInstant ^LocalDateTime v ZoneOffset/UTC)
    (instance? OffsetDateTime v) (.toInstant ^OffsetDateTime v)
    (instance? ZonedDateTime v) (.toInstant ^ZonedDateTime v)
    (instance? Date v) (.toInstant ^Date v)
    (string? v) (let [s (str/replace v \space \T)
                      s (if (or (str/ends-with? s "Z")
                                (re-find #"[+-]\d{2}:?\d{2}$" s))
                          s
                          (str s "Z"))]
                  (Instant/parse s))
    :else (throw (ex-info (str "Cannot coerce to Instant: " (class v))
                          {:value v :type (class v)}))))

(defn to-utc-timestamp-str
  "Coerces a datetime value into an Instant, then formats it as a UTC string without timezone
   suffix (YYYY-MM-DD HH:MM:SS.SSS) to store raw UTC wall-clock time in DB."
  [v]
  (when-let [inst (->instant v)]
    (.format utc-formatter inst)))
