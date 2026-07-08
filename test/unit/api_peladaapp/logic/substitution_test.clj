(ns api-peladaapp.logic.substitution-test
  (:require
   [api-peladaapp.logic.substitution :as sub.logic]
   [clojure.test :refer [deftest is testing]]))

(deftest test-ensure-distinct-players
  (testing "Throws when player IDs are the same"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Players must differ"
         (sub.logic/ensure-distinct-players "player-1" "player-1"))))

  (testing "Does not throw when player IDs are different"
    (is (nil? (sub.logic/ensure-distinct-players "player-1" "player-2")))))

(deftest test-ensure-players-belong
  (testing "Throws when player does not belong"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Players must belong to pelada teams"
         (sub.logic/ensure-players-belong #{"player-1" "player-2"} ["player-1" "player-3"]))))

  (testing "Does not throw when all players belong"
    (is (nil? (sub.logic/ensure-players-belong #{"player-1" "player-2"} ["player-1" "player-2"])))))

(deftest test-validate-substitution
  (testing "Throws when players are not distinct"
    (is (thrown?
         clojure.lang.ExceptionInfo
         (sub.logic/validate-substitution
          {:out-player-id "player-1" :in-player-id "player-1"}
          #{"player-1"}))))

  (testing "Throws when players do not belong"
    (is (thrown?
         clojure.lang.ExceptionInfo
         (sub.logic/validate-substitution
          {:out-player-id "player-1" :in-player-id "player-2"}
          #{"player-1"}))))

  (testing "Returns the substitution map when valid"
    (let [sub-map {:out-player-id "player-1" :in-player-id "player-2"}]
      (is (= sub-map
             (sub.logic/validate-substitution
              sub-map
              #{"player-1" "player-2"}))))))
