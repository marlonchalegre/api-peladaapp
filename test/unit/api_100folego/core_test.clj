(ns api-100folego.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-100folego.core :as core]))

(deftest sanity
  (testing "-main is defined"
    (is (fn? core/-main))))
