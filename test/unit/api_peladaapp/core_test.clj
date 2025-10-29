(ns api-peladaapp.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-peladaapp.core :as core]))

(deftest sanity
  (testing "-main is defined"
    (is (fn? core/-main))))
