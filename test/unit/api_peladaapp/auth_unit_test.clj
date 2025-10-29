(ns api-peladaapp.auth-unit-test
  (:require [clojure.test :refer [deftest is]]
            [api-peladaapp.controllers.auth :as controllers.auth]
            [api-peladaapp.db.user :as db.user]
            [api-peladaapp.config :as config]
            [buddy.hashers :as hashers]))

(deftest authenticate-checks-password-and-returns-token
  (let [plain "s3cret"
        hashed (hashers/encrypt plain)
        user {:id 1 :email "u@e.com" :password hashed}
        db (fn [] nil)
        ;; stub find-user-by-email
        find-called (atom nil)]
    (with-redefs [db.user/find-user-by-email (fn [_ _] (reset! find-called true) user)
                  config/get-key (fn [_] "dev-secret")]
      (let [result (controllers.auth/authenticate {:email (:email user) :password plain} db)]
        (is (map? result))
        (is (string? (:token result)))
        (is (= user (:user result)))
        (is @find-called)))))

(deftest authenticate-invalid-password
  (let [plain "s3cret"
        hashed (hashers/encrypt "different")
        user {:id 1 :email "u@e.com" :password hashed}
        db (fn [] nil)]
    (with-redefs [db.user/find-user-by-email (fn [_ _] user)
                  config/get-key (fn [_] "dev-secret")]
      (is (thrown? Exception (controllers.auth/authenticate {:email (:email user) :password plain} db))))))
