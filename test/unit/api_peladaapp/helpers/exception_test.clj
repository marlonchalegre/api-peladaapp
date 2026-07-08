(ns api-peladaapp.helpers.exception-test
  (:require
   [api-peladaapp.helpers.exception :as ex.helper]
   [clojure.test :refer [deftest is testing]]))

(deftest test-api-exception-handler
  (testing "Handles nil ex-data (unexpected exceptions)"
    (let [ex (Exception. "unexpected error")
          response (ex.helper/api-exception-handler ex)]
      (is (= 500 (:status response)))
      (is (= "server-error" (get-in response [:body :error])))))

  (testing "Handles ex-data with specific types"
    (testing :already-exist
      (let [ex (ex-info "msg" {:type :already-exist :message "username exists"})
            response (ex.helper/api-exception-handler ex)]
        (is (= 400 (:status response)))
        (is (= "username exists" (get-in response [:body :message])))))

    (testing :not-found
      (let [ex (ex-info "msg" {:type :not-found :message "user not found"})
            response (ex.helper/api-exception-handler ex)]
        (is (= 404 (:status response)))
        (is (= "user not found" (get-in response [:body :message])))))

    (testing :invalid-credentials
      (let [ex (ex-info "msg" {:type :invalid-credentials :message "invalid pwd"})
            response (ex.helper/api-exception-handler ex)]
        (is (= 401 (:status response)))
        (is (= "invalid pwd" (get-in response [:body :message])))))

    (testing :bad-request
      (let [ex (ex-info "msg" {:type :bad-request :message "bad input"})
            response (ex.helper/api-exception-handler ex)]
        (is (= 400 (:status response)))
        (is (= "bad input" (get-in response [:body :message])))))

    (testing :validation-error
      (let [ex (ex-info "msg" {:type :validation-error :message "invalid email"})
            response (ex.helper/api-exception-handler ex)]
        (is (= 400 (:status response)))
        (is (= "invalid email" (get-in response [:body :message])))))

    (testing :forbidden
      (let [ex (ex-info "msg" {:type :forbidden :message "not allowed"})
            response (ex.helper/api-exception-handler ex)]
        (is (= 403 (:status response)))
        (is (= "not allowed" (get-in response [:body :message])))))

    (testing :too-many-requests
      (let [ex (ex-info "msg" {:type :too-many-requests :message "rate limit"})
            response (ex.helper/api-exception-handler ex)]
        (is (= 429 (:status response)))
        (is (= "rate limit" (get-in response [:body :message])))))

    (testing "unmatched custom error types fall back to server-error"
      (let [ex (ex-info "msg" {:type :custom-unknown :message "something odd"})
            response (ex.helper/api-exception-handler ex)]
        (is (= 500 (:status response)))
        (is (= "server-error" (get-in response [:body :error])))))))
