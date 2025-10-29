(ns api-100folego.schedule-limit-test
  (:require [clojure.test :refer [deftest is]]
            [api-100folego.logic.schedule :as sch]))

(deftest schedule-with-limit-returns-vector
  (let [teams [1 2 3 4]
        ms (sch/schedule-matches-with-limit teams 2)]
    (is (vector? ms))
    (is (every? map? ms))
    (is (every? #(and (contains? % :home) (contains? % :away)) ms))))
