(ns api-peladaapp.user-phone-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest registration-with-phone-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        email "phone-test@example.com"
        phone "5511999999999"
        resp (app (-> (mock/request :post "/auth/register")
                      (mock/json-body {:name "Phone User"
                                       :username "phoneuser"
                                       :email email
                                       :password "password123"
                                       :phone phone})))
        body (th/decode-body resp)]
    (is (= 201 (:status resp)))
    (is (= phone (:phone body)))

    (testing "Verify in database"
      (let [user (first (jdbc/execute! ds ["select phone from Users where email = ?" email]))]
        (is (= phone (or (:phone user) (:Users/phone user))))))))

(deftest update-phone-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        email "update-phone@example.com"
        initial-phone "111111111"
        new-phone "222222222"
        token (th/register-and-login! app {:name "Update Phone User"
                                           :username "updatephone"
                                           :email email
                                           :password "pass"
                                           :phone initial-phone})
        user-id (th/user-id-by-email ds email)]

    (testing "Verify initial phone"
      (let [resp (app (-> (mock/request :get (str "/api/user/" user-id))
                          (mock/header "Authorization" (str "Token " token))))
            body (th/decode-body resp)]
        (is (= initial-phone (:phone body)))))

    (testing "Update phone"
      (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                          (mock/header "Authorization" (str "Token " token))
                          (mock/json-body {:phone new-phone})))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= new-phone (:phone body)))))

    (testing "Clear phone (set to nil/empty)"
      (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                          (mock/header "Authorization" (str "Token " token))
                          (mock/json-body {:phone ""})))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (or (nil? (:phone body)) (= "" (:phone body))))))))
