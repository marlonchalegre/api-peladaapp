(ns api-100folego.players-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [api-100folego.test-helpers :as th]))

(deftest players-crud
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "U" :email "u@e.com" :password "p"})
        auth (th/auth-header token)]
    (sql/insert! ds :organizations {:name "Org"})
    (sql/insert! ds :users {:name "Ana" :email "ana@example.com" :password "p"})

    ;; create
    (let [resp (app (-> (mock/request :post "/api/players")
                        (mock/json-body {:organization_id 1 :user_id 1})
                        auth))
          body (th/decode-body resp)]
      (is (= 201 (:status resp))))
    ;; read
    (let [resp (app (-> (mock/request :get "/api/players/1") auth))
          body (th/decode-body resp)]
      (is (= 200 (:status resp)))
      (is (= 1 (:user_id body))))
    ;; update
    (is (= 200 (:status (app (-> (mock/request :put "/api/players/1") (mock/json-body {:grade 8.5}) auth)))))
    ;; list by org
    (let [resp (app (-> (mock/request :get "/api/organizations/1/players") auth))
          body (th/decode-body resp)]
      (is (= 200 (:status resp)))
      (is (= 1 (count body))))
    ;; delete
    (let [resp (app (-> (mock/request :delete "/api/players/1") auth))]
      (is (= 200 (:status resp)))
      (is (= {} (th/decode-body resp))))))
