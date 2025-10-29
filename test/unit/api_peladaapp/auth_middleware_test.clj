(ns api-peladaapp.auth-middleware-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-peladaapp.handlers.auth :as auth]
            [buddy.auth :as buddy-auth]
            [buddy.auth.accessrules :as accessrules]))

(deftest authenticated-access-with-valid-token
  (testing "authenticated-access returns true when request is authenticated"
    (with-redefs [buddy-auth/authenticated? (fn [_] true)]
      (let [request {:identity {:user-id 1}}
            result (auth/authenticated-access request)]
        (is (true? result))))))

(deftest authenticated-access-without-token
  (testing "authenticated-access returns RuleError when request is not authenticated"
    (with-redefs [buddy-auth/authenticated? (fn [_] false)]
      (let [request {}
            result (auth/authenticated-access request)]
        ;; The error function from buddy returns a RuleError exception
        (is (instance? buddy.auth.accessrules.RuleError result))
        ;; The error message/data is embedded in the RuleError
        (is (string? (str result)))))))

(deftest authenticated-access-with-invalid-token
  (testing "authenticated-access returns RuleError when token is invalid"
    (with-redefs [buddy-auth/authenticated? (fn [_] false)]
      (let [request {:headers {"authorization" "Token invalid-token"}}
            result (auth/authenticated-access request)]
        (is (instance? buddy.auth.accessrules.RuleError result))))))

(deftest admin-access-with-admin-user
  (testing "admin-access returns true when user is authenticated and is admin"
    (with-redefs [buddy-auth/authenticated? (fn [_] true)]
      (let [request {:identity {:user-id 1 :is-admin? true}}
            result (auth/admin-access request)]
        (is (true? result))))))

(deftest admin-access-with-non-admin-user
  (testing "admin-access returns RuleError when user is authenticated but not admin"
    (with-redefs [buddy-auth/authenticated? (fn [_] true)]
      (let [request {:identity {:user-id 1 :is-admin? false}}
            result (auth/admin-access request)]
        (is (instance? buddy.auth.accessrules.RuleError result))))))

(deftest admin-access-without-authentication
  (testing "admin-access returns RuleError when user is not authenticated"
    (with-redefs [buddy-auth/authenticated? (fn [_] false)]
      (let [request {}
            result (auth/admin-access request)]
        (is (instance? buddy.auth.accessrules.RuleError result))))))

(deftest admin-access-without-identity
  (testing "admin-access returns RuleError when authenticated but no identity"
    (with-redefs [buddy-auth/authenticated? (fn [_] true)]
      (let [request {}
            result (auth/admin-access request)]
        (is (instance? buddy.auth.accessrules.RuleError result))))))
