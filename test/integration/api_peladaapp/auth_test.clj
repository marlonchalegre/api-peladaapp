(ns api-peladaapp.auth-test
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [buddy.hashers :as hashers]
   [clojure.test :refer [deftest is use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest login-success-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    (jdbc/execute! ds (hsql/format (-> (h/insert-into :Users)
                                       (h/values [{:name "John"
                                                   :email "john@example.com"
                                                   :password (hashers/encrypt "s3cret")}]))))
    (let [resp (app (-> (mock/request :post "/auth/login")
                        (mock/json-body {:email "john@example.com" :password "s3cret"})))
          body (th/decode-body resp)]
      (is (= 200 (:status resp)))
      (is (contains? body :token))
      (let [set-cookie (get-in resp [:headers "Set-Cookie"])
            cookie-str (if (coll? set-cookie) (first set-cookie) (str set-cookie))]
        (is (some? cookie-str))
        (is (re-find #"Max-Age=2592000" cookie-str))))))

(deftest sliding-session-renews-cookie-on-api-request-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    (jdbc/execute! ds (hsql/format (-> (h/insert-into :Users)
                                       (h/values [{:name "Alice"
                                                   :email "alice@example.com"
                                                   :password (hashers/encrypt "s3cret")}]))))
    (let [login-resp (app (-> (mock/request :post "/auth/login")
                              (mock/json-body {:email "alice@example.com" :password "s3cret"})))
          token (:token (th/decode-body login-resp))]
      (is (= 200 (:status login-resp)))
      (is (string? token))
      ;; Call /api/users/me with the authToken cookie
      (let [me-resp (app (-> (mock/request :get "/api/users/me")
                             (mock/cookie "authToken" token)))
            set-cookie (get-in me-resp [:headers "Set-Cookie"])
            cookie-str (if (coll? set-cookie) (first set-cookie) (str set-cookie))]
        (is (= 200 (:status me-resp)))
        (is (some? cookie-str))
        (is (re-find #"Max-Age=2592000" cookie-str))))))

(deftest login-fails-with-wrong-password
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    (jdbc/execute! ds (hsql/format (-> (h/insert-into :Users)
                                       (h/values [{:name "John"
                                                   :email "john@example.com"
                                                   :password (hashers/encrypt "s3cret")}]))))
    (let [resp (app (-> (mock/request :post "/auth/login")
                        (mock/json-body {:email "john@example.com" :password "bad"})))]
      (is (= 401 (:status resp))))))

(deftest login-fails-with-non-existent-user
  (let [app (-> th/*test-system* :app :app-handler)
        resp (app (-> (mock/request :post "/auth/login")
                      (mock/json-body {:email "ghost@example.com" :password "any"})))]
    (is (= 401 (:status resp)))))
