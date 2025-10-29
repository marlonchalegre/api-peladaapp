(ns api-peladaapp.users-test
  (:require [clojure.test :refer [deftest is]]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [api-peladaapp.test-helpers :as th]))

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
  (let [{:keys [app]} (th/make-app!)
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
