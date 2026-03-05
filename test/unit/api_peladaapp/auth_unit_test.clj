(ns api-peladaapp.auth-unit-test
  (:require
   [api-peladaapp.config :as config]
   [api-peladaapp.controllers.auth :as controllers.auth]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.user :as db.user]
   [buddy.hashers :as hashers]
   [clojure.test :refer [deftest is]]))

(deftest authenticate-checks-password-and-returns-token
  (let [plain "s3cret"
        hashed (hashers/encrypt plain)
        user {:id 1 :email "u@e.com" :password hashed}
        db (fn [] nil)
        find-called (atom nil)]
    (with-redefs [db.user/find-user-by-identifier (fn [_ _] (reset! find-called true) user)
                  db.admin/list-organizations-by-admin (fn [_ _] [])
                  config/get-key (fn [_] "dev-secret")]
      (let [result (controllers.auth/authenticate {:email (:email user) :password plain} db)]
        (is (map? result))
        (is (string? (:token result)))
        (is (= (assoc user :admin-orgs []) (:user result)))
        (is @find-called)))))

(deftest authenticate-by-username-test
  (let [plain "s3cret"
        hashed (hashers/encrypt plain)
        user {:id 1 :username "testuser" :password hashed}
        db (fn [] nil)
        find-called (atom nil)]
    (with-redefs [db.user/find-user-by-identifier (fn [identifier _]
                                                    (reset! find-called identifier)
                                                    (when (= identifier "testuser") user))
                  db.admin/list-organizations-by-admin (fn [_ _] [])
                  config/get-key (fn [_] "dev-secret")]
      (let [result (controllers.auth/authenticate {:email "testuser" :password plain} db)]
        (is (map? result))
        (is (= "testuser" @find-called))
        (is (= (assoc user :admin-orgs []) (:user result)))))))

(deftest authenticate-invalid-password
  (let [plain "s3cret"
        hashed (hashers/encrypt "different")
        user {:id 1 :email "u@e.com" :password hashed}
        db (fn [] nil)]
    (with-redefs [db.user/find-user-by-identifier (fn [_ _] user)
                  db.admin/list-organizations-by-admin (fn [_ _] [])
                  config/get-key (fn [_] "dev-secret")]
      (is (thrown? Exception (controllers.auth/authenticate {:email (:email user) :password plain} db))))))
