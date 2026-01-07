(ns api-peladaapp.core-test
  (:require
   [api-peladaapp.core :as core]
   [clojure.test :refer [deftest is testing]]))

(deftest sanity
  (testing "-main is defined"
    (is (fn? core/-main))))
