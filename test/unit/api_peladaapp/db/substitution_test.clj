(ns api-peladaapp.db.substitution-test
  (:require
   [api-peladaapp.db.substitution :as db.sub]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [next.jdbc :as jdbc]))

(deftest test-insert-substitution
  (let [match-id (random-uuid)
        out-player-id (random-uuid)
        in-player-id (random-uuid)
        sub-id (random-uuid)]
    (with-redefs [jdbc/execute-one! (fn [_ query _]
                                      (is (str/includes? (first query) "INSERT INTO"))
                                      {:id sub-id})]
      (is (= sub-id (db.sub/insert-substitution {:match-id match-id
                                                 :minute 15
                                                 :out-player-id out-player-id
                                                 :in-player-id in-player-id}
                                                "dummy-db"))))))

(deftest test-list-substitutions
  (let [match-id (random-uuid)
        mock-subs [{:id (random-uuid) :match-id match-id}]]
    (with-redefs [jdbc/execute! (fn [_ query _]
                                  (is (str/includes? (first query) "SELECT"))
                                  (is (some #{match-id} query))
                                  mock-subs)]
      (is (= mock-subs (db.sub/list-substitutions match-id "dummy-db"))))))

