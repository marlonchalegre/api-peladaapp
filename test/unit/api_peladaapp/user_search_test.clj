(ns api-peladaapp.user-search-test
  (:require
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.test-helpers :as th]
   [clojure.set :as set]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each th/test-system-fixture)

(deftest search-users-test
  (let [ds (th/get-test-datasource)]
    (testing "setup users"
      (db.user/insert-user {:name "Alice Smith" :email "alice@example.com" :password "pass"} ds)
      (db.user/insert-user {:name "Bob Jones" :email "bob@example.com" :password "pass"} ds)
      (db.user/insert-user {:name "Charlie Brown" :email "charlie@gmail.com" :password "pass"} ds))

    (testing "search by name"
      (let [results (db.user/search-users ds "Alice" 0 10)]
        (is (= 1 (count results)))
        (is (= "Alice Smith" (:name (first results))))))

    (testing "search by partial email"
      (let [results (db.user/search-users ds "example.com" 0 10)]
        (is (= 2 (count results)))
        (is (some #(= "Alice Smith" (:name %)) results))
        (is (some #(= "Bob Jones" (:name %)) results))))

    (testing "case insensitive search"
      (let [results (db.user/search-users ds "alice" 0 10)]
        (is (= 1 (count results)))
        (is (= "Alice Smith" (:name (first results))))))

    (testing "pagination"
      (let [results-p1 (db.user/search-users ds "e" 0 2)
            results-p2 (db.user/search-users ds "e" 2 2)]
        (is (= 2 (count results-p1)))
        (is (= 1 (count results-p2)))
        ;; Ensure we got different users (Alice/Bob vs Charlie)
        (let [names-p1 (set (map :name results-p1))
              names-p2 (set (map :name results-p2))]
          (is (empty? (set/intersection names-p1 names-p2))))))

    (testing "empty results"
      (let [results (db.user/search-users ds "NonExistent" 0 10)]
        (is (= 0 (count results)))))

    (testing "count searched users"
      (is (= 2 (db.user/count-searched-users ds "example.com")))
      (is (= 3 (db.user/count-searched-users ds "e")))
      (is (= 0 (db.user/count-searched-users ds "XYZ"))))))
