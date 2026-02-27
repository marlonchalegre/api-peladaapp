(ns api-peladaapp.auth-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [buddy.hashers :as hashers]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest login-success-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (sql/insert! ds :users {:name "John"
                            :email "john@example.com"
                            :password (hashers/encrypt "s3cret")})
    (let [resp (app (-> (mock/request :post "/auth/login")
                        (mock/json-body {:email "john@example.com" :password "s3cret"})))
          body (th/decode-body resp)]
      (is (= 200 (:status resp)))
      (is (contains? body :token)))))

(deftest login-fails-with-wrong-password
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (sql/insert! ds :users {:name "John"
                            :email "john@example.com"
                            :password (hashers/encrypt "s3cret")})
    (let [resp (app (-> (mock/request :post "/auth/login")
                        (mock/json-body {:email "john@example.com" :password "bad"})))]
      (is (= 401 (:status resp))))))

(deftest login-fails-with-non-existent-user
  (let [app (-> th/*test-system* :app :handler)
        resp (app (-> (mock/request :post "/auth/login")
                      (mock/json-body {:email "ghost@example.com" :password "any"})))]
    (is (= 401 (:status resp)))))
