(ns api-peladaapp.authorization-test
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.test :refer [deftest is testing]]))

(deftest test-get-user-id-from-request
  (testing "Extract user ID from request identity"
    (let [request {:identity {:id 123}}]
      (is (= 123 (auth/get-user-id-from-request request)))))

  (testing "Returns nil when identity is missing"
    (let [request {}]
      (is (nil? (auth/get-user-id-from-request request))))))

(deftest test-user-can-admin-organization?
  (testing "Returns true when user is admin of organization"
    (let [mock-db (fn [] nil)
          result (with-redefs [db.admin/is-user-admin-of-organization?
                               (fn [_ _ _] true)]
                   (auth/user-can-admin-organization? 1 1 mock-db))]
      (is (true? result))))

  (testing "Returns false when user is not admin"
    (let [mock-db (fn [] nil)
          result (with-redefs [db.admin/is-user-admin-of-organization?
                               (fn [_ _ _] false)]
                   (auth/user-can-admin-organization? 1 1 mock-db))]
      (is (false? result)))))

(deftest test-user-is-in-organization?
  (testing "Returns true when user is admin"
    (let [mock-db (fn [] nil)]
      (with-redefs [db.admin/is-user-admin-of-organization?
                    (fn [_ _ _] true)
                    db.player/get-org-player-by-user-id
                    (fn [_ _ _] nil)]
        (is (true? (auth/user-is-in-organization? 1 1 mock-db))))))

  (testing "Returns true when user is player"
    (let [mock-db (fn [] nil)]
      (with-redefs [db.admin/is-user-admin-of-organization?
                    (fn [_ _ _] false)
                    db.player/get-org-player-by-user-id
                    (fn [_ _ _] {:id 1})]
        (is (true? (auth/user-is-in-organization? 1 1 mock-db))))))

  (testing "Returns false when user is neither admin nor player"
    (let [mock-db (fn [] nil)]
      (with-redefs [db.admin/is-user-admin-of-organization?
                    (fn [_ _ _] false)
                    db.player/get-org-player-by-user-id
                    (fn [_ _ _] nil)]
        (is (false? (auth/user-is-in-organization? 1 1 mock-db)))))))

(deftest test-require-organization-admin!
  (testing "Does not throw when user is admin"
    (let [mock-db (fn [] nil)]
      (with-redefs [db.admin/is-user-admin-of-organization?
                    (fn [_ _ _] true)]
        (is (nil? (auth/require-organization-admin! 1 1 mock-db))))))

  (testing "Throws exception when user is not admin"
    (let [mock-db (fn [] nil)]
      (with-redefs [db.admin/is-user-admin-of-organization?
                    (fn [_ _ _] false)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"User is not an admin of this organization"
                              (auth/require-organization-admin! 1 1 mock-db)))))))

(deftest test-require-organization-member!
  (testing "Does not throw when user is member"
    (let [mock-db (fn [] nil)]
      (with-redefs [db.admin/is-user-admin-of-organization?
                    (fn [_ _ _] true)
                    db.player/get-org-player-by-user-id
                    (fn [_ _ _] nil)]
        (is (nil? (auth/require-organization-member! 1 1 mock-db))))))

  (testing "Throws exception when user is not member"
    (let [mock-db (fn [] nil)]
      (with-redefs [db.admin/is-user-admin-of-organization?
                    (fn [_ _ _] false)
                    db.player/get-org-player-by-user-id
                    (fn [_ _ _] nil)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"User is not a member of this organization"
                              (auth/require-organization-member! 1 1 mock-db)))))))
