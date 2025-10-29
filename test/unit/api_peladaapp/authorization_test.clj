(ns api-peladaapp.authorization-test
  (:require [clojure.test :refer [deftest testing is]]
            [api-peladaapp.logic.authorization :as auth]))

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
          result (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                               (fn [_ _ _] true)]
                   (auth/user-can-admin-organization? 1 1 mock-db))]
      (is (true? result))))
  
  (testing "Returns false when user is not admin"
    (let [mock-db (fn [] nil)
          result (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                               (fn [_ _ _] false)]
                   (auth/user-can-admin-organization? 1 1 mock-db))]
      (is (false? result)))))

(deftest test-user-is-in-organization?
  (testing "Returns true when user is admin"
    (let [mock-db (fn [] nil)]
      (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                    (fn [_ _ _] true)
                    api-peladaapp.db.player/list-players-by-organization
                    (fn [_ _] [])]
        (is (true? (auth/user-is-in-organization? 1 1 mock-db))))))
  
  (testing "Returns true when user is player"
    (let [mock-db (fn [] nil)]
      (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                    (fn [_ _ _] false)
                    api-peladaapp.db.player/list-players-by-organization
                    (fn [_ _] [{:user_id 1}])]
        (is (true? (auth/user-is-in-organization? 1 1 mock-db))))))
  
  (testing "Returns false when user is neither admin nor player"
    (let [mock-db (fn [] nil)]
      (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                    (fn [_ _ _] false)
                    api-peladaapp.db.player/list-players-by-organization
                    (fn [_ _] [{:user_id 2}])]
        (is (false? (auth/user-is-in-organization? 1 1 mock-db)))))))

(deftest test-require-organization-admin!
  (testing "Does not throw when user is admin"
    (let [mock-db (fn [] nil)]
      (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                    (fn [_ _ _] true)]
        (is (nil? (auth/require-organization-admin! 1 1 mock-db))))))
  
  (testing "Throws exception when user is not admin"
    (let [mock-db (fn [] nil)]
      (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                    (fn [_ _ _] false)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo 
                              #"User is not an admin of this organization"
                              (auth/require-organization-admin! 1 1 mock-db)))))))

(deftest test-require-organization-member!
  (testing "Does not throw when user is member"
    (let [mock-db (fn [] nil)]
      (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                    (fn [_ _ _] true)
                    api-peladaapp.db.player/list-players-by-organization
                    (fn [_ _] [])]
        (is (nil? (auth/require-organization-member! 1 1 mock-db))))))
  
  (testing "Throws exception when user is not member"
    (let [mock-db (fn [] nil)]
      (with-redefs [api-peladaapp.db.admin/is-user-admin-of-organization? 
                    (fn [_ _ _] false)
                    api-peladaapp.db.player/list-players-by-organization
                    (fn [_ _] [])]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo 
                              #"User is not a member of this organization"
                              (auth/require-organization-member! 1 1 mock-db)))))))
