(ns api-peladaapp.user-profile-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [api-peladaapp.test-helpers :as th]
            [buddy.hashers :as hashers]))

(defn- register! [app {:keys [name email password]}]
  (app (-> (mock/request :post "/auth/register")
           (mock/json-body {:name name :email email :password password}))))

(defn- login! [app {:keys [email password]}]
  (app (-> (mock/request :post "/auth/login")
           (mock/json-body {:email email :password password}))))

(defn- decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when (not (str/blank? b)) (json/read-str b :key-fn keyword))
      (instance? java.io.InputStream b) (let [s (slurp b)] (when (not (str/blank? s)) (json/read-str s :key-fn keyword)))
      :else nil)))

(deftest user-profile-update-name
  (testing "User can update their name via profile endpoint"
    (let [{:keys [app]} (th/make-app!)
          email "john@example.com"
          password "password123"]
      ;; Register user
      (let [reg (register! app {:name "John Doe" :email email :password password})]
        (is (= 201 (:status reg))))
      ;; Login
      (let [login (login! app {:email email :password password})
            body (decode-body login)
            token (:token body)]
        (is (= 200 (:status login)))
        (is (string? token))
        ;; Update profile - name only
        (let [resp (app (-> (mock/request :put "/api/user/1/profile")
                            (mock/header "authorization" (str "Token " token))
                            (mock/json-body {:name "John Smith"})))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (= "John Smith" (:name body)))
          (is (= email (:email body))))
        ;; Verify the update persisted
        (let [resp (app (-> (mock/request :get "/api/user/1")
                            (mock/header "authorization" (str "Token " token))))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (= "John Smith" (:name body))))))))

(deftest user-profile-update-email
  (testing "User can update their email via profile endpoint"
    (let [{:keys [app]} (th/make-app!)
          email "jane@example.com"
          new-email "jane.updated@example.com"
          password "password123"]
      ;; Register user
      (let [reg (register! app {:name "Jane Doe" :email email :password password})]
        (is (= 201 (:status reg))))
      ;; Login
      (let [login (login! app {:email email :password password})
            body (decode-body login)
            token (:token body)]
        (is (= 200 (:status login)))
        ;; Update profile - email only
        (let [resp (app (-> (mock/request :put "/api/user/1/profile")
                            (mock/header "authorization" (str "Token " token))
                            (mock/json-body {:email new-email})))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (= new-email (:email body)))
          (is (= "Jane Doe" (:name body))))))))

(deftest user-profile-update-password
  (testing "User can update their password via profile endpoint"
    (let [{:keys [app db]} (th/make-app!)
          email "bob@example.com"
          old-password "oldpass123"
          new-password "newpass456"]
      ;; Register user
      (let [reg (register! app {:name "Bob" :email email :password old-password})]
        (is (= 201 (:status reg))))
      ;; Login with old password
      (let [login (login! app {:email email :password old-password})
            body (decode-body login)
            token (:token body)]
        (is (= 200 (:status login)))
        ;; Update password
        (let [resp (app (-> (mock/request :put "/api/user/1/profile")
                            (mock/header "authorization" (str "Token " token))
                            (mock/json-body {:password new-password})))]
          (is (= 200 (:status resp))))
        ;; Verify old password no longer works
        (let [login-old (login! app {:email email :password old-password})]
          (is (not= 200 (:status login-old))))
        ;; Verify new password works
        (let [login-new (login! app {:email email :password new-password})]
          (is (= 200 (:status login-new))))))))

(deftest user-profile-update-multiple-fields
  (testing "User can update name and email together"
    (let [{:keys [app]} (th/make-app!)
          email "alice@example.com"
          password "password123"]
      ;; Register user
      (let [reg (register! app {:name "Alice" :email email :password password})]
        (is (= 201 (:status reg))))
      ;; Login
      (let [login (login! app {:email email :password password})
            body (decode-body login)
            token (:token body)]
        (is (= 200 (:status login)))
        ;; Update multiple fields
        (let [resp (app (-> (mock/request :put "/api/user/1/profile")
                            (mock/header "authorization" (str "Token " token))
                            (mock/json-body {:name "Alice Wonder" :email "alice.wonder@example.com"})))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (= "Alice Wonder" (:name body)))
          (is (= "alice.wonder@example.com" (:email body))))))))

(deftest user-profile-update-not-found
  (testing "Returns 403 when user tries to update another user (even if non-existent)"
    (let [{:keys [app]} (th/make-app!)
          email "test@example.com"
          password "password123"]
      ;; Register and login as user 1
      (register! app {:name "Test" :email email :password password})
      (let [login (login! app {:email email :password password})
            body (decode-body login)
            token (:token body)]
        ;; Try to update non-existent user 999
        ;; Should return 403 because user 1 cannot update user 999 (authorization fails first)
        (let [resp (app (-> (mock/request :put "/api/user/999/profile")
                            (mock/header "authorization" (str "Token " token))
                            (mock/json-body {:name "New Name"})))]
          (is (= 403 (:status resp)))
          (is (= "You can only update your own profile" (:body resp))))))))

(deftest user-profile-update-empty-body
  (testing "Handles empty update gracefully"
    (let [{:keys [app]} (th/make-app!)
          email "empty@example.com"
          password "password123"]
      ;; Register user
      (let [reg (register! app {:name "Empty" :email email :password password})]
        (is (= 201 (:status reg))))
      ;; Login
      (let [login (login! app {:email email :password password})
            body (decode-body login)
            token (:token body)]
        ;; Update with empty body
        (let [resp (app (-> (mock/request :put "/api/user/1/profile")
                            (mock/header "authorization" (str "Token " token))
                            (mock/json-body {})))
              body (decode-body resp)]
          ;; Should succeed but not change anything
          (is (= 200 (:status resp)))
          (is (= "Empty" (:name body)))
          (is (= email (:email body))))))))

(deftest user-profile-update-authorization-own-profile
  (testing "User can update their own profile"
    (let [{:keys [app]} (th/make-app!)
          email "auth-test@example.com"
          password "password123"]
      ;; Register user with ID 1
      (let [reg (register! app {:name "Auth Test" :email email :password password})]
        (is (= 201 (:status reg))))
      ;; Login
      (let [login (login! app {:email email :password password})
            body (decode-body login)
            token (:token body)
            user-id (-> body :user :id)]
        ;; Update own profile (user ID 1 updating user ID 1)
        (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                            (mock/header "authorization" (str "Token " token))
                            (mock/json-body {:name "Updated Name"})))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (= "Updated Name" (:name body))))))))

(deftest user-profile-update-authorization-blocks-other-users
  (testing "User cannot update another user's profile"
    (let [{:keys [app]} (th/make-app!)
          email1 "user1@example.com"
          email2 "user2@example.com"
          password "password123"]
      ;; Register two users
      (register! app {:name "User 1" :email email1 :password password})
      (register! app {:name "User 2" :email email2 :password password})
      ;; Login as user 2
      (let [login (login! app {:email email2 :password password})
            body (decode-body login)
            token (:token body)]
        ;; Try to update user 1's profile while logged in as user 2
        (let [resp (app (-> (mock/request :put "/api/user/1/profile")
                            (mock/header "authorization" (str "Token " token))
                            (mock/json-body {:name "Hacked Name"})))]
          ;; Should return 403 Forbidden
          (is (= 403 (:status resp)))
          (is (= "You can only update your own profile" (:body resp))))
        ;; Verify user 1's profile was not changed
        (let [login1 (login! app {:email email1 :password password})
              body1 (decode-body login1)
              token1 (:token body1)
              get-resp (app (-> (mock/request :get "/api/user/1")
                               (mock/header "authorization" (str "Token " token1))))
              get-body (decode-body get-resp)]
          (is (= "User 1" (:name get-body))))))))
