(ns api-peladaapp.users-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

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

(deftest users-crud-flow
  (let [app (-> th/*test-system* :app :handler)
        email "ana@example.com"
        password "topsecret"]
    (let [reg (register! app {:name "Ana" :email email :password password})]
      (is (= 201 (:status reg))))
    (let [login (login! app {:email email :password password})
          body (decode-body login)
          token (:token body)]
      (is (= 200 (:status login)))
      (is (string? token))
      ;; read
      (let [resp (app (-> (mock/request :get "/api/user/1")
                          (mock/header "authorization" (str "Token " token))))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= email (:email body))))
      ;; update
      (let [resp (app (-> (mock/request :put "/api/user/1")
                          (mock/header "authorization" (str "Token " token))
                          (mock/json-body {:name "Ana Maria"})))]
        (is (= 200 (:status resp))))
      ;; delete (now JSON 200)
      (let [resp (app (-> (mock/request :delete "/api/user/1")
                          (mock/header "authorization" (str "Token " token))))]
        (is (= 200 (:status resp))))
      ;; ensure gone
      (let [resp (app (-> (mock/request :get "/api/user/1")
                          (mock/header "authorization" (str "Token " token))))]
        (is (= 404 (:status resp)))))))

(deftest users-pagination
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "U" :email "u@e.com" :password "p"})
        auth (th/auth-header token)]
    ;; Create 25 users
    (doseq [i (range 25)]
      (sql/insert! ds :users {:name (str "User " i) :email (str "user" i "@example.com") :password "p"}))

    ;; Test first page
    (let [resp (app (-> (mock/request :get "/api/users?page=1&per_page=10")
                        auth))
          body (th/decode-body resp)
          headers (:headers resp)]
      (is (= 200 (:status resp)))
      (is (= 10 (count body)))
      (is (= "26" (get headers "X-Total")))
      (is (= "3" (get headers "X-Total-Pages")))
      (is (= "10" (get headers "X-Per-Page")))
      (is (= "1" (get headers "X-Page"))))

    ;; Test second page
    (let [resp (app (-> (mock/request :get "/api/users?page=2&per_page=10")
                        auth))
          body (th/decode-body resp)
          headers (:headers resp)]
      (is (= 200 (:status resp)))
      (is (= 10 (count body)))
      (is (= "2" (get headers "X-Page"))))

    ;; Test last page
    (let [resp (app (-> (mock/request :get "/api/users?page=3&per_page=10")
                        auth))
          body (th/decode-body resp)
          headers (:headers resp)]
      (is (= 200 (:status resp)))
      (is (= 6 (count body)))
      (is (= "3" (get headers "X-Page"))))))
