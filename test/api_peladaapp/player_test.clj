(ns api-peladaapp.player-test
  (:require
   [api-peladaapp.db.player :as db.player]
   [clojure.test :refer [deftest is testing]]))

(deftest position-string->id-test
  (testing "mappings for various cases"
    (let [f #'api-peladaapp.db.player/position-string->id]
      (is (= 1 (f "goalkeeper")))
      (is (= 1 (f "Goalkeeper")))
      (is (= 1 (f "GOALKEEPER")))
      (is (= 2 (f "defender")))
      (is (= 2 (f "Defender")))
      (is (= 3 (f "midfielder")))
      (is (= 3 (f "Midfielder")))
      (is (= 4 (f "striker")))
      (is (= 4 (f "Striker")))
      (is (nil? (f "unknown")))
      (is (nil? (f nil))))))
