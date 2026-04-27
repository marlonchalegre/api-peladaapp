(ns api-peladaapp.health-test
  (:require
   [api-peladaapp.handlers.health :as health]
   [api-peladaapp.logic.waha :as waha]
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
