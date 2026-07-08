(ns api-peladaapp.password-reset-test
  (:require
   [api-peladaapp.db.password-reset :as db.password-reset]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.password-reset :as logic.password-reset]
   [api-peladaapp.test-helpers :as th]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [postal.core :as postal]))

(use-fixtures :each th/test-system-fixture)

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest password-reset-flow-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        email "test@example.com"
        password "old-password"
        new-password "new-password"]

    ;; Create a user
    (db.user/insert-user {:name "Test User" :username "testuser" :email email :password password} ds)

    (testing "request password reset"
      (with-redefs [postal/send-message (fn [_ _] {:code 0 :error :SUCCESS})]
        (logic.password-reset/request-password-reset! email ds)

        (let [tokens (exec! ds (-> (h/select :*) (h/from :password_reset_tokens)))]
          (is (= 1 (count tokens)))
          (let [token (:token (first tokens))]
            (is (string? token))

            (testing "reset password with valid token"
              (let [result (logic.password-reset/reset-password! token new-password ds)]
                (is (true? result))

                (let [user (db.user/find-user-by-identifier email ds)]
                  (is (not= password (:password user)))
                  (is (= "Test User" (:name user)) "User name should not be lost")
                  ;; Verify we can't use the token again
                  (is (false? (logic.password-reset/reset-password! token "another-password" ds))))))

            (testing "token is deleted after use"
              (let [tokens-after (exec! ds (-> (h/select :*) (h/from :password_reset_tokens)))]
                (is (= 0 (count tokens-after)))))))))

    (testing "reset password with invalid token"
      (is (false? (logic.password-reset/reset-password! "invalid-token" "any-password" ds))))

    (testing "request password reset with invalid identifier or missing email"
      ;; identifier doesn't exist
      (is (nil? (logic.password-reset/request-password-reset! "nonexistent@e.com" ds)))
      ;; user exists but has no email
      (db.user/insert-user {:name "No Email User" :username "noemailreset" :password "pass"} ds)
      (is (nil? (logic.password-reset/request-password-reset! "noemailreset" ds))))

    (testing "send-reset-email! exception handling"
      (with-redefs [postal/send-message (fn [_ _] (throw (Exception. "SMTP connection failed")))]
        (is (nil? (logic.password-reset/send-reset-email! "error@example.com" "some-token")))))

    (testing "reset password with expired token"
      ;; Let's insert an expired token manually
      (let [user-id (db.user/insert-user {:name "Expired User" :username "expiredreset" :email "expired@e.com" :password "pass"} ds)
            token "expired-token"
            expired-at "2020-01-01T00:00:00Z"]
        (db.password-reset/create-token! user-id token expired-at ds)
        (is (false? (logic.password-reset/reset-password! token "newpass" ds)))))

    (testing "generate-reset-link structure"
      ;; Since generate-reset-link is private (defn-), we can test it indirectly via send-reset-email!
      (with-redefs [postal/send-message (fn [_ msg] (is (str/includes? (get-in msg [:body 0 :content]) "/reset-password?token=custom-tok")))]
        (logic.password-reset/send-reset-email! "env@example.com" "custom-tok")))))



