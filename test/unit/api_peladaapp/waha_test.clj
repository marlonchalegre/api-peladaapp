(ns api-peladaapp.waha-test
  (:require
   [api-peladaapp.logic.waha :as waha]
   [clj-http.client :as http]
   [clojure.test :refer [deftest is testing]]))

(deftest healthcheck-test
  (testing "Returns UP when WAHA is running"
    (with-redefs [http/get (fn [_ _] {:status 200 :body "{\"version\": \"1.0.0\"}"})]
      (let [result (waha/healthcheck)]
        (is (= "UP" (:status result)))
        (is (= "1.0.0" (get-in result [:details "version"]))))))

  (testing "Returns DOWN when WAHA returns error status"
    (with-redefs [http/get (fn [_ _] {:status 500})]
      (let [result (waha/healthcheck)]
        (is (= "DOWN" (:status result)))
        (is (re-find #"Unexpected status" (:error result))))))

  (testing "Returns DOWN when request fails"
    (with-redefs [http/get (fn [_ _] (throw (Exception. "Connection refused")))]
      (let [result (waha/healthcheck)]
        (is (= "DOWN" (:status result)))
        (is (= "Connection refused" (:error result)))))))

(deftest resume-session-test
  (testing "Returns success when session starts"
    (with-redefs [http/post (fn [_ _] {:status 200})]
      (let [result (waha/resume-session "default")]
        (is (= "success" (:status result)))
        (is (re-find #"started/resumed" (:message result))))))

  (testing "Returns error when session start fails"
    (with-redefs [http/post (fn [_ _] {:status 404})]
      (let [result (waha/resume-session "default")]
        (is (= "error" (:status result)))
        (is (re-find #"Unexpected status: 404" (:error result)))))))

(deftest start-session-test
  (testing "Returns success when session created"
    (with-redefs [http/post (fn [_ _] {:status 201})]
      (let [result (waha/start-session "default")]
        (is (= "success" (:status result)))
        (is (re-find #"created and started" (:message result)))))))

(deftest stop-session-test
  (testing "Returns success when session deleted"
    (with-redefs [http/delete (fn [_ _] {:status 204})]
      (let [result (waha/stop-session "default")]
        (is (= "success" (:status result)))
        (is (re-find #"stopped/deleted" (:message result)))))))

(deftest restart-session-test
  (testing "Restarts session successfully"
    (with-redefs [waha/stop-session (fn [_] {:status "success"})
                  waha/start-session (fn [_] {:status "success"})
                  waha/sleep (fn [_] nil)]
      (let [result (waha/restart-session "default")]
        (is (= "success" (:status result)))))))

(deftest normalize-phone-test
  (testing "Brazilian numbers (DDD 11-28) - keep 9th digit"
    (is (= "5511999999999@c.us" (waha/normalize-phone "5511999999999")))
    (is (= "5521988887777@c.us" (waha/normalize-phone "+55 (21) 98888-7777"))))

  (testing "Brazilian numbers (DDD > 28) - remove 9th digit"
    (is (= "554188887777@c.us" (waha/normalize-phone "5541988887777")))
    (is (= "554188887777@c.us" (waha/normalize-phone "+55 (41) 98888-7777"))))

  (testing "Brazilian numbers (DDD > 28) - keep if 8 digits"
    (is (= "554188887777@c.us" (waha/normalize-phone "554188887777"))))

  (testing "Non-Brazilian numbers"
    (is (= "12025550123@c.us" (waha/normalize-phone "+1 202-555-0123"))))

  (testing "Empty or nil phone"
    (is (nil? (waha/normalize-phone "")))
    (is (nil? (waha/normalize-phone nil)))))
