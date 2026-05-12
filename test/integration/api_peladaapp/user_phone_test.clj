(ns api-peladaapp.user-phone-test
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- exec-one! [ds query]
  (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest registration-with-phone-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
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
      (let [user (exec-one! ds (-> (h/select :phone) (h/from :Users) (h/where [:= :email email])))]
        (is (= phone (:phone user)))))))

(deftest update-phone-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
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
                          (th/auth-cookie token)))
            body (th/decode-body resp)]
        (is (= initial-phone (:phone body)))))

    (testing "Update phone"
      (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                          (th/auth-cookie token)
                          (mock/json-body {:phone new-phone})))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= new-phone (:phone body)))))

    (testing "Clear phone (set to nil/empty)"
      (let [resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                          (th/auth-cookie token)
                          (mock/json-body {:phone ""})))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (or (nil? (:phone body)) (= "" (:phone body))))))))
