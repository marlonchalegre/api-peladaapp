(ns api-peladaapp.auth-middleware-test
  (:require
   [api-peladaapp.config :as config]
   [api-peladaapp.controllers.auth :as controllers.auth]
   [api-peladaapp.controllers.user :as controllers.user]
   [api-peladaapp.handlers.auth :as auth]
   [api-peladaapp.logic.authorization :as auth.logic]
   [api-peladaapp.logic.password-reset :as logic.password-reset]
   [buddy.auth :as buddy-auth]
   [buddy.sign.jwt :as jwt]
   [clojure.test :refer [deftest is testing]]))

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
      (let [request {:cookies {"authToken" {:value "invalid-token"}}}
            result (auth/authenticated-access request)]
        (is (instance? buddy.auth.accessrules.RuleError result))))))

(deftest admin-access-with-admin-user
  (testing "admin-access returns true when user is authenticated and is admin"
    (with-redefs [buddy-auth/authenticated? (fn [_] true)]
      (let [request {:identity {:user-id 1 :is-global-admin? true}}
            result (auth/admin-access request)]
        (is (true? result))))))

(deftest admin-access-with-non-admin-user
  (testing "admin-access returns RuleError when user is authenticated but not admin"
    (with-redefs [buddy-auth/authenticated? (fn [_] true)]
      (let [request {:identity {:user-id 1 :is-global-admin? false}}
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

(deftest test-extract-token
  (testing "extracts token from string key cookie"
    (is (= "token-123" (#'auth/extract-token {:cookies {"authToken" {:value "token-123"}}}))))
  (testing "extracts token from keyword key cookie"
    (is (= "token-456" (#'auth/extract-token {:cookies {:authToken {:value "token-456"}}}))))
  (testing "returns nil if no cookie is present"
    (is (nil? (#'auth/extract-token {})))
    (is (nil? (#'auth/extract-token {:cookies {}})))))

(deftest test-wrap-manual-auth
  (let [secret "jwt-secret-key"
        valid-token "valid-token"
        claims {:id "00000000-0000-0000-0000-000000000001" :admin_orgs ["00000000-0000-0000-0000-000000000002"]}]
    (testing "when no token is present, handler is called without identity"
      (let [handler-called (atom false)
            middleware (auth/wrap-manual-auth (fn [req]
                                                (is (nil? (:identity req)))
                                                (reset! handler-called true)))]
        (middleware {})
        (is @handler-called)))

    (testing "when valid token is present, parses identity into request"
      (let [handler-called (atom false)
            middleware (auth/wrap-manual-auth (fn [req]
                                                (is (= (parse-uuid (:id claims)) (get-in req [:identity :id])))
                                                (is (= [(parse-uuid (first (:admin_orgs claims)))] (get-in req [:identity :admin_orgs])))
                                                (reset! handler-called true)))]
        (with-redefs [config/get-key (fn [k] (if (= k :jwt-secret) secret nil))
                      jwt/unsign (fn [tok sec opts]
                                   (is (= valid-token tok))
                                   (is (= secret sec))
                                   (is (= :hs512 (:alg opts)))
                                   claims)]
          (middleware {:cookies {"authToken" {:value valid-token}}})
          (is @handler-called))))

    (testing "when token unsign throws exception, handler is called without identity"
      (let [handler-called (atom false)
            middleware (auth/wrap-manual-auth (fn [req]
                                                (is (nil? (:identity req)))
                                                (reset! handler-called true)))]
        (with-redefs [config/get-key (fn [_] secret)
                      jwt/unsign (fn [& _] (throw (Exception. "Invalid Signature")))]
          (middleware {:cookies {"authToken" {:value "invalid-token"}}})
          (is @handler-called))))))

(deftest test-brute-force-protection
  (let [attempts-atom (atom {})]
    (testing "too-many-attempts? returns false initially"
      (is (false? (#'auth/too-many-attempts? attempts-atom "user1"))))

    (testing "recording failures increments count"
      (#'auth/record-failure attempts-atom "user1")
      (is (= 1 (get-in @attempts-atom ["user1" :count])))
      (#'auth/record-failure attempts-atom "user1")
      (is (= 2 (get-in @attempts-atom ["user1" :count]))))

    (testing "too-many-attempts? returns true after 5 failures"
      (dotimes [_ 3] (#'auth/record-failure attempts-atom "user1"))
      (is (true? (#'auth/too-many-attempts? attempts-atom "user1"))))

    (testing "too-many-attempts? returns false if lockout expired"
      (swap! attempts-atom assoc "user1" {:count 5 :last-attempt (- (System/currentTimeMillis) (* 20 60 1000))})
      (is (false? (#'auth/too-many-attempts? attempts-atom "user1"))))

    (testing "recording failure after lockout expired resets count to 1"
      (swap! attempts-atom assoc "user1" {:count 5 :last-attempt (- (System/currentTimeMillis) (* 20 60 1000))})
      (#'auth/record-failure attempts-atom "user1")
      (is (= 1 (get-in @attempts-atom ["user1" :count]))))

    (testing "clear-attempts removes the entry"
      (#'auth/clear-attempts attempts-atom "user1")
      (is (nil? (get @attempts-atom "user1"))))))

(deftest test-auth-handlers
  (testing "auth-handler handles lockout and successful login"
    (reset! auth/login-attempts {})
    (let [db nil]
      (testing "when locked out, returns too many requests exception response"
        (swap! auth/login-attempts assoc "locked@test.com" {:count 6 :last-attempt (System/currentTimeMillis)})
        (let [resp (auth/auth-handler {:body {:email "locked@test.com" :password "pass"} :database db})]
          (is (= 429 (:status resp)))))

      (testing "on successful login, clears attempts and sets token cookie"
        (reset! auth/login-attempts {"ok@test.com" {:count 3 :last-attempt (System/currentTimeMillis)}})
        (with-redefs [controllers.auth/authenticate (fn [_ _] {:token "token1" :user {:id "u1" :email "ok@test.com"}})]
          (let [resp (auth/auth-handler {:body {:email "ok@test.com" :password "pass"} :database db})]
            (is (= 200 (:status resp)))
            (is (empty? @auth/login-attempts))
            (is (= "token1" (get-in resp [:cookies "authToken" :value]))))))

      (testing "on failed login, records failure"
        (reset! auth/login-attempts {})
        (with-redefs [controllers.auth/authenticate (fn [_ _] (throw (ex-info "Invalid credentials" {:type :invalid-credentials})))]
          (let [resp (auth/auth-handler {:body {:email "bad@test.com" :password "wrong"} :database db})]
            (is (= 401 (:status resp)))
            (is (= 1 (get-in @auth/login-attempts ["bad@test.com" :count]))))))))

  (testing "forgot-password-handler handles lockout"
    (reset! auth/password-reset-attempts {})
    (let [db nil]
      (swap! auth/password-reset-attempts assoc "locked@test.com" {:count 6 :last-attempt (System/currentTimeMillis)})
      (let [resp (auth/forgot-password-handler {:body {:email "locked@test.com"} :database db})]
        (is (= 429 (:status resp))))))

  (testing "logout-handler clears authToken cookie"
    (let [resp (auth/logout-handler {})]
      (is (= 200 (:status resp)))
      (is (= "" (get-in resp [:cookies "authToken" :value])))
      (is (= 0 (get-in resp [:cookies "authToken" :max-age]))))))

(deftest test-get-me-handler
  (let [db "dummy-db"
        user-uuid (random-uuid)
        mock-user {:id user-uuid :email "ok@test.com" :name "John"}]
    (testing "get-me successfully"
      (with-redefs [auth.logic/get-user-id-from-request (fn [_] user-uuid)
                    controllers.user/get-user (fn [id _]
                                                (is (= user-uuid id))
                                                mock-user)]
        (let [resp (auth/get-me-handler {:database db})]
          (is (= 200 (:status resp)))
          (is (= "ok@test.com" (:email (:body resp)))))))

    (testing "get-me exception caught"
      (with-redefs [auth.logic/get-user-id-from-request (fn [_] (throw (Exception. "Failed to get user")))]
        (let [resp (auth/get-me-handler {:database db})]
          (is (= 500 (:status resp))))))))

(deftest test-forgot-password-handler-success-and-error
  (let [db "dummy-db"]
    (testing "forgot-password successfully"
      (reset! auth/password-reset-attempts {})
      (with-redefs [logic.password-reset/request-password-reset! (fn [email _]
                                                                   (is (= "ok@test.com" email))
                                                                   true)]
        (let [resp (auth/forgot-password-handler {:body {:email "ok@test.com"} :database db})]
          (is (= 200 (:status resp)))
          (is (= 1 (get-in @auth/password-reset-attempts ["ok@test.com" :count]))))))

    (testing "forgot-password exception caught"
      (reset! auth/password-reset-attempts {})
      (with-redefs [logic.password-reset/request-password-reset! (fn [_ _] (throw (Exception. "Forgot error")))]
        (let [resp (auth/forgot-password-handler {:body {:email "ok@test.com"} :database db})]
          (is (= 500 (:status resp))))))))

(deftest test-reset-password-handler
  (let [db "dummy-db"]
    (testing "reset-password successfully"
      (with-redefs [logic.password-reset/reset-password! (fn [tok pass _]
                                                           (is (= "token123" tok))
                                                           (is (= "new-pass" pass))
                                                           true)]
        (let [resp (auth/reset-password-handler {:body {:token "token123" :password "new-pass"} :database db})]
          (is (= 200 (:status resp)))
          (is (= "Password reset successfully." (get-in resp [:body :message]))))))

    (testing "reset-password with invalid token"
      (with-redefs [logic.password-reset/reset-password! (fn [_ _ _] false)]
        (let [resp (auth/reset-password-handler {:body {:token "invalid" :password "pass"} :database db})]
          (is (= 400 (:status resp))))))

    (testing "reset-password exception caught"
      (with-redefs [logic.password-reset/reset-password! (fn [_ _ _] (throw (Exception. "Reset error")))]
        (let [resp (auth/reset-password-handler {:body {:token "tok" :password "pass"} :database db})]
          (is (= 500 (:status resp))))))))

(deftest test-first-access-handler
  (let [db "dummy-db"
        user-uuid (random-uuid)
        mock-user {:id user-uuid :email "ok@test.com" :name "John"}]
    (testing "first-access successfully"
      (with-redefs [controllers.auth/first-access (fn [body _]
                                                    (is (= "payload" body))
                                                    {:token "token123" :user mock-user})]
        (let [resp (auth/first-access-handler {:body "payload" :database db})]
          (is (= 200 (:status resp)))
          (is (= "token123" (get-in resp [:cookies "authToken" :value]))))))

    (testing "first-access exception caught"
      (with-redefs [controllers.auth/first-access (fn [_ _] (throw (Exception. "First access error")))]
        (let [resp (auth/first-access-handler {:body "payload" :database db})]
          (is (= 500 (:status resp))))))))


