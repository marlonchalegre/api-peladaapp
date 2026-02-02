(ns api-peladaapp.user-profile-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [buddy.hashers :as hashers]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
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
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "Profile User" :email "profile@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "profile@example.com")]
    (let [resp (app (-> (mock/request :get (str "/api/user/" user-id))
                        (mock/header "Authorization" (str "Token " token))))
          body (decode-body resp)]
      (is (= 200 (:status resp)))
      (is (= "Profile User" (:name body)))
      (is (= "profile@example.com" (:email body))))))

(deftest get-user-profile-not-found
  (let [app (-> th/*test-system* :app :handler)
        token (th/register-and-login! app {:name "Any" :email "any@any.com" :password "any"})]
    (let [resp (app (-> (mock/request :get "/api/user/9999")
                        (mock/header "Authorization" (str "Token " token))))]
      (is (= 404 (:status resp))))))

(deftest update-user-profile-success
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "Old Name" :email "old@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "old@example.com")]
    (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                        (mock/header "Authorization" (str "Token " token))
                        (mock/json-body {:name "New Name" :position "Midfielder"})))
          body (decode-body resp)]
      (is (= 200 (:status resp)))
      (is (= "New Name" (:name body)))
      (is (= "Midfielder" (:position body))))))

(deftest delete-user-success
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "Delete Me" :email "delete@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "delete@example.com")]
    (let [resp (app (-> (mock/request :delete (str "/api/user/" user-id))
                        (mock/header "Authorization" (str "Token " token))))]
      (is (= 204 (:status resp)))
      (is (nil? (th/user-id-by-email ds "delete@example.com"))))))
