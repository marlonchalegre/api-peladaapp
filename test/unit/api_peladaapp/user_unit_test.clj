(ns api-peladaapp.user-unit-test
  (:require
   [api-peladaapp.controllers.user :as controller.user]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.user :as logic.user]
   [clojure.test :refer [deftest is testing]]))

(deftest create-user-test
  (let [db (fn [] nil)
        new-user {:name "New User" :username "newuser" :email "new@e.com" :password "pass"}
        user-no-email {:name "No Email" :username "noemail" :password "pass"}]

    (testing "Create user with all fields"
      (with-redefs [db.user/find-user-by-username (fn [_ _] nil)
                    db.user/find-user-by-email (fn [_ _] nil)
                    db.user/insert-user (fn [_ _] 100)
                    logic.user/encrypt-password (fn [u] u)]
        (let [result (controller.user/create-user new-user db)]
          (is (= 100 (:id result)))
          (is (= "newuser" (:username result))))))

    (testing "Create user without email"
      (with-redefs [db.user/find-user-by-username (fn [_ _] nil)
                    db.user/find-user-by-email (fn [_ _] nil)
                    db.user/insert-user (fn [_ _] 101)
                    logic.user/encrypt-password (fn [u] u)]
        (let [result (controller.user/create-user user-no-email db)]
          (is (= 101 (:id result)))
          (is (nil? (:email result))))))

    (testing "Fails if username already exists"
      (with-redefs [db.user/find-user-by-username (fn [_ _] {:id 1 :username "newuser" :password "hash"})
                    db.user/find-user-by-email (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Username already exists"
                              (controller.user/create-user new-user db)))))

    (testing "Fails if email already exists"
      (with-redefs [db.user/find-user-by-username (fn [_ _] nil)
                    db.user/find-user-by-email (fn [_ _] {:id 1 :email "new@e.com" :password "hash"})]
        (is (thrown-with-msg? Exception #"Email already exists"
                              (controller.user/create-user new-user db)))))))
