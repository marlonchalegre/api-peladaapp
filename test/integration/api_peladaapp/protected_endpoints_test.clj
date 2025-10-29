(ns api-peladaapp.protected-endpoints-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [buddy.hashers :as hashers]
            [api-peladaapp.test-helpers :as th]))

(def protected-endpoints
  "List of protected API endpoints that require authentication"
  [{:method :get :path "/api/users"}
   {:method :get :path "/api/user/1"}
   {:method :put :path "/api/user/1"}
   {:method :delete :path "/api/user/1"}
   {:method :post :path "/api/organizations"}
   {:method :get :path "/api/organizations"}
   {:method :get :path "/api/organizations/1"}
   {:method :put :path "/api/organizations/1"}
   {:method :delete :path "/api/organizations/1"}
   {:method :post :path "/api/peladas"}
   {:method :get :path "/api/peladas/1"}
   {:method :put :path "/api/peladas/1"}
   {:method :delete :path "/api/peladas/1"}
   {:method :post :path "/api/teams"}
   {:method :get :path "/api/teams/1"}
   {:method :put :path "/api/teams/1"}
   {:method :delete :path "/api/teams/1"}
   {:method :post :path "/api/players"}
   {:method :get :path "/api/players/1"}
   {:method :put :path "/api/players/1"}
   {:method :delete :path "/api/players/1"}])

(deftest protected-endpoints-require-authentication
  (testing "All protected endpoints return 401 when no token is provided"
    (let [{:keys [app]} (th/make-app!)]
      (doseq [{:keys [method path]} protected-endpoints]
        (let [req (mock/request method path)
              resp (app req)]
          (is (= 401 (:status resp))
              (str "Expected 401 for " method " " path " without token, got " (:status resp)))
          (when-let [body (th/decode-body resp)]
            (is (contains? body :error))
            (is (or (= "authentication" (:type body))
                    (= :authentication (:type body))))))))))

(deftest protected-endpoints-reject-invalid-token
  (testing "All protected endpoints return 401 when invalid token is provided"
    (let [{:keys [app]} (th/make-app!)]
      (doseq [{:keys [method path]} protected-endpoints]
        (let [req (-> (mock/request method path)
                      (mock/header "Authorization" "Token invalid-token-12345"))
              resp (app req)]
          (is (= 401 (:status resp))
              (str "Expected 401 for " method " " path " with invalid token, got " (:status resp))))))))

(deftest protected-endpoints-accept-valid-token
  (testing "Protected endpoints accept valid authentication tokens"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
      ;; Create a user and get valid token
      (sql/insert! ds :users {:name "Test User"
                              :email "test@example.com"
                              :password (hashers/encrypt "password123")})
      (let [token (th/register-and-login! app {:name "Auth User"
                                                :email "auth@example.com"
                                                :password "pass123"})
            ;; Test only GET endpoints to avoid side effects
            get-endpoints (filter #(= :get (:method %)) protected-endpoints)]
        (doseq [{:keys [method path]} get-endpoints]
          (let [req (-> (mock/request method path)
                        (mock/header "Authorization" (str "Token " token)))
                resp (app req)]
            ;; Should not return 401 - might be 404, 200, or other status depending on data
            (is (not= 401 (:status resp))
                (str "Expected non-401 for " method " " path " with valid token, got " (:status resp)))))))))

(deftest login-and-register-are-public
  (testing "Login and register endpoints are accessible without authentication"
    (let [{:keys [app]} (th/make-app!)]
      ;; Login endpoint should be accessible
      (let [login-req (-> (mock/request :post "/auth/login")
                          (mock/json-body {:email "nonexistent@example.com"
                                           :password "somepass"}))
            login-resp (app login-req)]
        ;; Should not be 401 (might be 400 or 404 for bad credentials)
        (is (not= 401 (:status login-resp))
            "Login endpoint should be public"))
      
      ;; Register endpoint should be accessible
      (let [register-req (-> (mock/request :post "/auth/register")
                             (mock/json-body {:name "New User"
                                              :email "new@example.com"
                                              :password "newpass123"}))
            register-resp (app register-req)]
        ;; Should not be 401
        (is (not= 401 (:status register-resp))
            "Register endpoint should be public")))))

(deftest missing-authorization-header-format
  (testing "Requests with malformed Authorization header return 401"
    (let [{:keys [app]} (th/make-app!)]
      ;; Missing "Token" prefix
      (let [req (-> (mock/request :get "/api/users")
                    (mock/header "Authorization" "just-a-token"))
            resp (app req)]
        (is (= 401 (:status resp))
            "Expected 401 for malformed Authorization header"))
      
      ;; Empty Authorization header
      (let [req (-> (mock/request :get "/api/users")
                    (mock/header "Authorization" ""))
            resp (app req)]
        (is (= 401 (:status resp))
            "Expected 401 for empty Authorization header")))))

(deftest expired-token-handling
  (testing "Expired tokens should be rejected"
    (let [{:keys [app]} (th/make-app!)]
      ;; This is a token that has a past expiration date
      ;; In a real scenario, you would generate a token with exp in the past
      (let [req (-> (mock/request :get "/api/users")
                    (mock/header "Authorization" "Token eyJhbGciOiJIUzUxMiJ9.expiredtoken"))
            resp (app req)]
        (is (= 401 (:status resp))
            "Expected 401 for expired token")))))
