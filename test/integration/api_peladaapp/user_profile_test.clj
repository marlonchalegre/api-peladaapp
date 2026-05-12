(ns api-peladaapp.user-profile-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when (not (str/blank? b)) (json/read-str b :key-fn keyword))
      (instance? java.io.InputStream b) (let [s (slurp b)] (when (not (str/blank? s)) (json/read-str s :key-fn keyword)))
      :else nil)))

(deftest get-user-profile-success
  (let [app (-> th/*test-system* :app :app-handler)
        db-file (:db-file th/*test-system*)
        ds (api-peladaapp.test-helpers/get-test-datasource db-file)
        token (th/register-and-login! app {:name "Profile User" :email "profile@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "profile@example.com")
        resp (app (-> (mock/request :get (str "/api/user/" user-id))
                      (mock/cookie "authToken" token)))
        body (decode-body resp)]
    (is (= 200 (:status resp)))
    (is (= "Profile User" (:name body)))
    (is (= "profile@example.com" (:email body)))))

(deftest get-user-profile-unauthorized
  (let [app (-> th/*test-system* :app :app-handler)
        token (th/register-and-login! app {:name "Any" :email "any@any.com" :password "any"})
        resp (app (-> (mock/request :get "/api/user/9999")
                      (mock/cookie "authToken" token)))]
    (is (= 403 (:status resp)))))

(deftest update-user-profile-success
  (let [app (-> th/*test-system* :app :app-handler)
        db-file (:db-file th/*test-system*)
        ds (api-peladaapp.test-helpers/get-test-datasource db-file)
        token (th/register-and-login! app {:name "Old Name" :email "old@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "old@example.com")
        resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                      (mock/cookie "authToken" token)
                      (mock/json-body {:name "New Name" :position "Midfielder"})))
        body (decode-body resp)]
    (is (= 200 (:status resp)))
    (is (= "New Name" (:name body)))
    (is (= "Midfielder" (:position body)))))

(deftest delete-user-success
  (let [app (-> th/*test-system* :app :app-handler)
        db-file (:db-file th/*test-system*)
        ds (api-peladaapp.test-helpers/get-test-datasource db-file)
        token (th/register-and-login! app {:name "Delete Me" :email "delete@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "delete@example.com")
        resp (app (-> (mock/request :delete (str "/api/user/" user-id))
                      (mock/cookie "authToken" token)))]
    (is (= 204 (:status resp)))
    (is (nil? (th/user-id-by-email ds "delete@example.com")))))

(deftest update-user-profile-duplicate-email
  (let [app (-> th/*test-system* :app :app-handler)
        db-file (:db-file th/*test-system*)
        ds (api-peladaapp.test-helpers/get-test-datasource db-file)
        ;; Register first user
        _ (th/register-and-login! app {:name "User 1" :username "user1" :email "user1@example.com" :password "pass"})
        ;; Register second user
        token2 (th/register-and-login! app {:name "User 2" :username "user2" :email "user2@example.com" :password "pass"})
        user2-id (th/user-id-by-email ds "user2@example.com")
        ;; Try to update second user's email to first user's email
        resp (app (-> (mock/request :put (str "/api/user/" user2-id "/profile"))
                      (mock/cookie "authToken" token2)
                      (mock/json-body {:email "user1@example.com"})))
        body (decode-body resp)]
    (is (= 400 (:status resp)))
    (is (= "Email already exists" (:message body)))))
