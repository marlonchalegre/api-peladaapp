(ns api-100folego.auth-unit-test
  (:require [clojure.test :refer :all]
            [api-100folego.controllers.auth :as controllers.auth]
            [buddy.hashers :as hashers]))

(deftest authenticate-checks-password-and-returns-token
  (let [plain "s3cret"
        hashed (hashers/encrypt plain)
        user {:id 1 :email "u@e.com" :password hashed}
        db (fn [] nil)
        ;; stub find-user-by-email
        find-called (atom nil)
        orig controllers.auth/authenticate]
    (with-redefs [api-100folego.db.user/find-user-by-email (fn [_ _] (do (reset! find-called true) user))
                  api-100folego.config/get-key (fn [_] "dev-secret")]
      (let [token (controllers.auth/authenticate {:email (:email user) :password plain} db)]
        (is (string? token))
        (is @find-called)))))

(deftest authenticate-invalid-password
  (let [plain "s3cret"
        hashed (hashers/encrypt "different")
        user {:id 1 :email "u@e.com" :password hashed}
        db (fn [] nil)]
    (with-redefs [api-100folego.db.user/find-user-by-email (fn [_ _] user)
                  api-100folego.config/get-key (fn [_] "dev-secret")]
      (is (thrown? Exception (controllers.auth/authenticate {:email (:email user) :password plain} db))))))
