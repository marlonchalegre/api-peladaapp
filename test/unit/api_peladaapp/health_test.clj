(ns api-peladaapp.health-test
  (:require
   [api-peladaapp.handlers.health :as health]
   [api-peladaapp.logic.waha :as waha]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]))

(deftest waha-healthcheck-handler-test
  (testing "Returns 200 when logic returns UP"
    (with-redefs [waha/healthcheck (fn [] {:status "UP" :details {"version" "1.0.0"}})]
      (let [response (health/waha-healthcheck {})]
        (is (= 200 (:status response)))
        (is (= "UP" (get-in response [:body :status]))))))

  (testing "Returns 503 when logic returns DOWN"
    (with-redefs [waha/healthcheck (fn [] {:status "DOWN" :error "Timeout"})]
      (let [response (health/waha-healthcheck {})]
        (is (= 503 (:status response)))
        (is (= "DOWN" (get-in response [:body :status])))))))

(deftest waha-resume-handler-test
  (testing "Returns 200 when resume is success"
    (with-redefs [waha/resume-session (fn [_] {:status "success" :message "OK"})]
      (let [response (health/waha-resume {})]
        (is (= 200 (:status response)))
        (is (= "success" (get-in response [:body :status]))))))

  (testing "Falls back to restart when resume fails"
    (with-redefs [waha/resume-session (fn [_] {:status "error"})
                  waha/restart-session (fn [_] {:status "success" :message "Restarted"})]
      (let [response (health/waha-resume {})]
        (is (= 200 (:status response)))
        (is (= "success" (get-in response [:body :status])))))))

(deftest waha-restart-handler-test
  (testing "Returns 200 when restart is success"
    (with-redefs [waha/restart-session (fn [_] {:status "success" :message "OK"})]
      (let [response (health/waha-restart {})]
        (is (= 200 (:status response)))
        (is (= "success" (get-in response [:body :status]))))))

  (testing "Returns 500 when restart fails"
    (with-redefs [waha/restart-session (fn [_] {:status "error" :error "Fail"})]
      (let [response (health/waha-restart {})]
        (is (= 500 (:status response)))
        (is (= "error" (get-in response [:body :status])))))))

(deftest check-handler-test
  (testing "Returns specified APP_VERSION environment variable"
    (with-redefs [health/get-env-version (fn [] "1.2.3")]
      (let [response (health/check {})]
        (is (= 200 (:status response)))
        (is (= "1.2.3" (get-in response [:body :version]))))))

  (testing "Falls back to resource version.txt when APP_VERSION is unset, 'latest', or 'development'"
    (with-redefs [health/get-env-version (fn [] "latest")
                  clojure.java.io/resource (fn [path] (if (= path "version.txt") (java.net.URL. "file:///tmp/dummy-version.txt") nil))
                  slurp (fn [_] "20260728-1900\n")]
      (let [response (health/check {})]
        (is (= 200 (:status response)))
        (is (= "20260728-1900" (get-in response [:body :version]))))))

  (testing "Returns 'development' when no version file exists and APP_VERSION is unset"
    (with-redefs [health/get-env-version (fn [] nil)
                  clojure.java.io/resource (fn [_] nil)]
      (let [response (health/check {})]
        (is (= 200 (:status response)))
        (is (= "development" (get-in response [:body :version])))))))
