(ns api-peladaapp.waha-test
  (:require
   [api-peladaapp.config :as config]
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

  (testing "Brazilian numbers (DDD > 28) - keep if 9 digits but not starting with 9"
    (is (= "5531888888888@c.us" (waha/normalize-phone "5531888888888"))))

  (testing "Non-Brazilian numbers"
    (is (= "12025550123@c.us" (waha/normalize-phone "+1 202-555-0123"))))

  (testing "Empty or nil phone"
    (is (nil? (waha/normalize-phone "")))
    (is (nil? (waha/normalize-phone nil)))))

(deftest send-message-test
  (let [config {:waha-api-url "http://waha:3000" :waha-instance "default" :waha-group-id "12345"}
        secret-key "waha-secret-123"]
    (testing "Sends message successfully with X-Api-Key when trusted"
      (with-redefs [config/get-key (fn [k] (if (= k :waha-api-key) secret-key nil))
                    http/post (fn [url opts]
                                (is (= "http://waha:3000/api/sendText" url))
                                (is (= secret-key (get-in opts [:headers "X-Api-Key"])))
                                (let [body (clojure.data.json/read-str (:body opts) :key-fn keyword)]
                                  (is (= "default" (:session body)))
                                  (is (= "12345" (:chatId body)))
                                  (is (= "hello" (:text body)))
                                  (is (nil? (:mentions body))))
                                {:status 200})]
        (is (= {:status 200} (waha/send-message config "hello")))))

    (testing "Sends message without X-Api-Key when untrusted"
      (with-redefs [config/get-key (fn [k] (if (= k :waha-api-key) secret-key nil))
                    http/post (fn [_ opts]
                                (is (nil? (get-in opts [:headers "X-Api-Key"])))
                                {:status 200})]
        (waha/send-message {:waha-api-url "http://untrusted-waha:3000" :waha-instance "default" :waha-group-id "12345"} "hello")))

    (testing "Includes mentions when provided"
      (with-redefs [http/post (fn [_ opts]
                                (let [body (clojure.data.json/read-str (:body opts) :key-fn keyword)]
                                  (is (= ["all"] (:mentions body))))
                                {:status 200})]
        (waha/send-message config "hello" ["all"])))

    (testing "Handles exception when HTTP post throws"
      (with-redefs [http/post (fn [_ _] (throw (Exception. "Failed to connect")))]
        (let [result (waha/send-message config "hello")]
          (is (= "Failed to connect" (:error result))))))))

(deftest send-poll-test
  (let [config {:waha-api-url "http://waha:3000" :waha-instance "default" :waha-group-id "12345"}]
    (testing "Sends poll successfully"
      (with-redefs [http/post (fn [url opts]
                                (is (= "http://waha:3000/api/sendPoll" url))
                                (let [body (clojure.data.json/read-str (:body opts) :key-fn keyword)]
                                  (is (= "default" (:session body)))
                                  (is (= "12345" (:chatId body)))
                                  (is (= "Question?" (get-in body [:poll :name])))
                                  (is (= ["Opt1" "Opt2"] (get-in body [:poll :options])))
                                  (is (false? (get-in body [:poll :multipleAnswers]))))
                                {:status 200})]
        (is (= {:status 200} (waha/send-poll config "Question?" ["Opt1" "Opt2"])))))

    (testing "Sends poll with multipleAnswers true when option is set"
      (with-redefs [http/post (fn [_ opts]
                                (let [body (clojure.data.json/read-str (:body opts) :key-fn keyword)]
                                  (is (true? (get-in body [:poll :multipleAnswers]))))
                                {:status 200})]
        (waha/send-poll config "Question?" ["Opt1" "Opt2"] true)))

    (testing "Handles exception when HTTP post throws"
      (with-redefs [http/post (fn [_ _] (throw (Exception. "Failed to post poll")))]
        (let [result (waha/send-poll config "Question?" ["Opt1" "Opt2"])]
          (is (= "Failed to post poll" (:error result))))))))

