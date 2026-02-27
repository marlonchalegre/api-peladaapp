(ns api-peladaapp.rate-limit-test
  (:require
   [api-peladaapp.test-helpers :refer [*test-system* test-system-fixture]]
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each test-system-fixture)

(deftest login-rate-limit-test
  (testing "should return 429 when too many login attempts occur"
    (let [system *test-system*
          handler (-> system :app :handler)
          email "rate-limit@test.com"
          password "password123"]
      ;; Trigger 5 failed attempts (or just enough to hit the limit)
      ;; Note: handlers/auth.clj uses an atom `login-attempts` which is global.

      ;; First 5 attempts should not be rate limited (they might fail with 401 though)
      (dotimes [_ 5]
        (let [response (handler (-> (mock/request :post "/auth/login")
                                    (mock/json-body {:email email :password password})))]
          (is (not= 429 (:status response)))))

      ;; 6th attempt should be rate limited
      (let [response (handler (-> (mock/request :post "/auth/login")
                                  (mock/json-body {:email email :password password})))
            body (json/read-str (:body response) :key-fn name)]
        (is (= 429 (:status response)))
        (is (= "Too many login attempts. Please try again later." (get body "message")))))))
