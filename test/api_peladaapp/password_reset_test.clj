(ns api-peladaapp.password-reset-test
  (:require
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.password-reset :as logic.password-reset]
   [api-peladaapp.test-helpers :as th]
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
                  ;; Verify we can't use the token again
                  (is (false? (logic.password-reset/reset-password! token "another-password" ds))))))

            (testing "token is deleted after use"
              (let [tokens-after (exec! ds (-> (h/select :*) (h/from :password_reset_tokens)))]
                (is (= 0 (count tokens-after)))))))))

    (testing "reset password with invalid token"
      (is (false? (logic.password-reset/reset-password! "invalid-token" "any-password" ds))))))
