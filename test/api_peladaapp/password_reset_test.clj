(ns api-peladaapp.password-reset-test
  (:require
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.password-reset :as logic.password-reset]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [postal.core :as postal]))

(use-fixtures :each th/test-system-fixture)

(deftest password-reset-flow-test
  (let [db-comp (:database th/*test-system*)
        db-val (:database db-comp)
        ds (if (fn? db-val) (db-val) db-val)
        email "test@example.com"
        password "old-password"
        new-password "new-password"]

    ;; Create a user
    (db.user/insert-user {:name "Test User" :username "testuser" :email email :password password} ds)

    (testing "request password reset"
      (with-redefs [postal/send-message (fn [_ _] {:code 0 :error :SUCCESS})]
        (logic.password-reset/request-password-reset! email ds)

        (let [tokens (jdbc/execute! ds ["SELECT * FROM password_reset_tokens"])]
          (is (= 1 (count tokens)))
          (let [token (or (:token (first tokens))
                          (:password_reset_tokens/token (first tokens)))]
            (is (string? token))

            (testing "reset password with valid token"
              (let [result (logic.password-reset/reset-password! token new-password ds)]
                (is (true? result))

                (let [user (db.user/find-user-by-identifier email ds)]
                  (is (not= password (:password user)))
                  ;; Verify we can't use the token again
                  (is (false? (logic.password-reset/reset-password! token "another-password" ds))))))

            (testing "token is deleted after use"
              (let [tokens-after (jdbc/execute! ds ["SELECT * FROM password_reset_tokens"])]
                (is (= 0 (count tokens-after)))))))))

    (testing "reset password with invalid token"
      (is (false? (logic.password-reset/reset-password! "invalid-token" "any-password" ds))))))
