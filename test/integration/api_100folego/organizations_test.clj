(ns api-100folego.organizations-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [api-100folego.test-helpers :as th]))

(deftest organizations-crud
  (let [{:keys [app]} (th/make-app!)
        token (th/register-and-login! app {:name "U" :email "u@e.com" :password "p"})
        auth (th/auth-header token)]
    ;; create
    (is (= 201 (:status (app (-> (mock/request :post "/api/organizations") (mock/json-body {:name "Org"}) auth)))))
    ;; list
    (is (= 200 (:status (app (-> (mock/request :get "/api/organizations") auth)))))
    ;; get
    (is (= 200 (:status (app (-> (mock/request :get "/api/organizations/1") auth)))))
    ;; update
    (is (= 200 (:status (app (-> (mock/request :put "/api/organizations/1") (mock/json-body {:name "New Org"}) auth)))))
    ;; delete (now JSON 200)
    (let [resp (app (-> (mock/request :delete "/api/organizations/1") auth))]
      (is (= 200 (:status resp)))
      (is (= {} (th/decode-body resp))))))
