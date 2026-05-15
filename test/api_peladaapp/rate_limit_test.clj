(ns api-peladaapp.rate-limit-test
  (:require
   [api-peladaapp.handlers.auth :as handlers.auth]
   [api-peladaapp.test-helpers :refer [*test-system* test-system-fixture]]
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each test-system-fixture)

(deftest login-rate-limit-test
  (testing "should return 429 when too many login attempts occur"
    (let [system *test-system*
          handler (-> system :app :app-handler)
          email "rate-limit@test.com"
          password "password123"]
      ;; Reset attempts before test
      (reset! handlers.auth/login-attempts {})

      ;; 5 attempts should trigger limit on the 6th
      (dotimes [_ 5]
        (handler (-> (mock/request :post "/auth/login")
                     (mock/json-body {:email email :password password}))))

      (let [response (handler (-> (mock/request :post "/auth/login")
                                  (mock/json-body {:email email :password password})))
            body (json/read-str (:body response) :key-fn name)]
        (is (= 429 (:status response)))
        (is (= "Too many login attempts. Please try again later." (get body "message"))))

      (testing "should reset lock when duration passed"
        ;; Manually set last-attempt to 20 minutes ago
        (swap! handlers.auth/login-attempts assoc email {:count 5 :last-attempt (- (System/currentTimeMillis) (* 20 60 1000))})
        
        ;; Should now work again (401 is expected if credentials are wrong, not 429)
        (let [response (handler (-> (mock/request :post "/auth/login")
                                    (mock/json-body {:email email :password password})))]
          (is (not= 429 (:status response))))))))
