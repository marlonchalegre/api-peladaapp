(ns api-100folego.auth-test
  (:require [clojure.test :refer [deftest is]]
            [ring.mock.request :as mock]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [buddy.hashers :as hashers]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [api-100folego.test-helpers :as th]))

(defn- decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when (not (str/blank? b)) (json/read-str b :key-fn keyword))
      (instance? java.io.InputStream b) (let [s (slurp b)] (when (not (str/blank? s)) (json/read-str s :key-fn keyword)))
      :else nil)))

(deftest login-success-returns-jwt
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (sql/insert! ds :users {:name "John"
                            :email "john@example.com"
                            :password (hashers/encrypt "s3cret")})
    (let [resp (app (-> (mock/request :post "/auth/login")
                        (mock/json-body {:email "john@example.com" :password "s3cret"})))
          body (decode-body resp)]
      (is (= 200 (:status resp)))
      (is (string? (:token body))))))

(deftest login-fails-with-wrong-password
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (sql/insert! ds :users {:name "John"
                            :email "john@example.com"
                            :password (hashers/encrypt "s3cret")})
    (let [resp (app (-> (mock/request :post "/auth/login")
                        (mock/json-body {:email "john@example.com" :password "bad"})))]
      (is (= 400 (:status resp))))))
