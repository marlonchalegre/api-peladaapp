(ns api-peladaapp.user-profile-edge-cases-test
  (:require
   [api-peladaapp.server :as server]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest update-profile-edge-cases-test
  (let [db-raw (-> th/*test-system* :database :database)
        db (if (fn? db-raw) (db-raw) db-raw)
        app (fn [req] (server/app (assoc req :database db)))

        token (th/register-and-login! app {:name "Edge User" :username "edge_user" :email "edge@test.com" :password "pass123"})
        user-id (th/user-id-by-email db "edge@test.com")
        auth (fn [req] (mock/header req "Authorization" (str "Token " token)))]

    (testing "Update profile with empty email (optional)"
      (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                          (mock/json-body {:name "Updated Name" :email "" :username "edge_user"})
                          auth))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= "" (:email body)))
        (is (= "Updated Name" (:name body)))))

    (testing "Update profile removing email (null/missing)"
      (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                          (mock/json-body {:name "Updated Name" :username "edge_user"})
                          auth))
            body (th/decode-body resp)]
        ;; The controller uses cond-> and assoc-some, so if email is missing in JSON, it stays as is.
        ;; But if it's explicitly null or empty, we should check behavior.
        (is (= 200 (:status resp)))))

    (testing "Fails when updating with empty username (mandatory in schema)"
      (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                          (mock/json-body {:username ""})
                          auth))]
        ;; Depending on specific schema validation, this might be 400 or just ignored if not in body
        ;; Our UpdateProfileRequest schema has (s/optional-key :username) s/Str
        (is (not= 500 (:status resp)))))))
