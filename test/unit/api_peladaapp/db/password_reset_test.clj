(ns api-peladaapp.db.password-reset-test
  (:require
   [api-peladaapp.db.password-reset :as db.pr]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [next.jdbc :as jdbc]))

(deftest test-create-token!
  (let [user-uuid (random-uuid)
        token "test-token"
        expires "2023-01-01T00:00:00Z"]
    (with-redefs [jdbc/execute-one! (fn [_ query]
                                      (is (re-find #"INSERT INTO .*password_reset_tokens" (first query)))
                                      (is (some #{user-uuid} query))
                                      {:id 1})]
      (is (= {:id 1} (db.pr/create-token! user-uuid token expires "db"))))))

(deftest test-find-token
  (let [token "test-token"
        mock-token {:user_id (random-uuid) :token token}]
    (with-redefs [jdbc/execute-one! (fn [_ query _]
                                      (is (str/includes? (first query) "SELECT"))
                                      (is (some #{token} query))
                                      mock-token)]
      (is (= {:user_id (:user_id mock-token) :token token} (db.pr/find-token token "db"))))))

(deftest test-delete-token!
  (let [token "test-token"]
    (with-redefs [jdbc/execute-one! (fn [_ query]
                                      (is (re-find #"DELETE FROM .*password_reset_tokens" (first query)))
                                      (is (some #{token} query))
                                      {:update-count 1})]
      (is (= {:update-count 1} (db.pr/delete-token! token "db"))))))

(deftest test-delete-user-tokens!
  (let [user-uuid (random-uuid)]
    (with-redefs [jdbc/execute-one! (fn [_ query]
                                      (is (re-find #"DELETE FROM .*password_reset_tokens" (first query)))
                                      (is (some #{user-uuid} query))
                                      {:update-count 1})]
      (is (= {:update-count 1} (db.pr/delete-user-tokens! user-uuid "db"))))))

(deftest test-delete-expired-tokens!
  (let [now "2023-01-01T00:00:00Z"]
    (with-redefs [jdbc/execute! (fn [_ query]
                                  (is (re-find #"DELETE FROM .*password_reset_tokens" (first query)))
                                  (is (some #{now} query))
                                  [{:update-count 1}])]
      (is (= [{:update-count 1}] (db.pr/delete-expired-tokens! now "db"))))))

