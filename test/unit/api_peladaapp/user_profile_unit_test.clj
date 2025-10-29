(ns api-peladaapp.user-profile-unit-test
  (:require [clojure.test :refer [deftest is testing]]
            [api-peladaapp.controllers.user :as controllers.user]
            [api-peladaapp.db.user :as db.user]
            [api-peladaapp.handlers.user]
            [buddy.hashers :as hashers]))

(deftest update-user-profile-updates-name
  (testing "Updates user name only"
    (let [user-id 1
          existing-user {:id 1 :name "Old Name" :email "user@test.com" :password "hashed"}
          profile-data {:name "New Name"}
          db (fn [] nil)
          find-called (atom 0)
          update-called (atom nil)]
      (with-redefs [db.user/find-user-by-id (fn [_ _] 
                                               (swap! find-called inc)
                                               (if (= @find-called 1)
                                                 existing-user
                                                 (assoc existing-user :name "New Name")))
                    db.user/update-user-profile (fn [_ user _] 
                                                  (reset! update-called user) 
                                                  1)]
        (let [result (controllers.user/update-user-profile profile-data user-id db)]
          (is (= "New Name" (:name result)))
          (is (= "user@test.com" (:email result)))
          (is (= "New Name" (:name @update-called))))))))

(deftest update-user-profile-updates-email
  (testing "Updates user email only"
    (let [user-id 1
          existing-user {:id 1 :name "User Name" :email "old@test.com" :password "hashed"}
          profile-data {:email "new@test.com"}
          db (fn [] nil)
          find-called (atom 0)
          update-called (atom nil)]
      (with-redefs [db.user/find-user-by-id (fn [_ _] 
                                               (swap! find-called inc)
                                               (if (= @find-called 1)
                                                 existing-user
                                                 (assoc existing-user :email "new@test.com")))
                    db.user/update-user-profile (fn [_ user _] 
                                                  (reset! update-called user) 
                                                  1)]
        (let [result (controllers.user/update-user-profile profile-data user-id db)]
          (is (= "new@test.com" (:email result)))
          (is (= "User Name" (:name result)))
          (is (= "new@test.com" (:email @update-called))))))))

(deftest update-user-profile-updates-password
  (testing "Updates user password and encrypts it"
    (let [user-id 1
          existing-user {:id 1 :name "User Name" :email "user@test.com" :password "old-hashed"}
          profile-data {:password "newpassword123"}
          db (fn [] nil)
          find-called (atom 0)
          update-called (atom nil)]
      (with-redefs [db.user/find-user-by-id (fn [_ _] 
                                               (swap! find-called inc)
                                               (if (= @find-called 1)
                                                 existing-user
                                                 (assoc existing-user :password "new-hashed")))
                    db.user/update-user-profile (fn [_ user _] 
                                                  (reset! update-called user) 
                                                  1)]
        (let [result (controllers.user/update-user-profile profile-data user-id db)]
          ;; Password should be encrypted
          (is (not= "newpassword123" (:password @update-called)))
          ;; Password should be a bcrypt hash
          (is (hashers/check "newpassword123" (:password @update-called)))
          (is (= "User Name" (:name result)))
          (is (= "user@test.com" (:email result))))))))

(deftest update-user-profile-updates-multiple-fields
  (testing "Updates name and email together"
    (let [user-id 1
          existing-user {:id 1 :name "Old Name" :email "old@test.com" :password "hashed"}
          profile-data {:name "New Name" :email "new@test.com"}
          db (fn [] nil)
          find-called (atom 0)
          update-called (atom nil)]
      (with-redefs [db.user/find-user-by-id (fn [_ _] 
                                               (swap! find-called inc)
                                               (if (= @find-called 1)
                                                 existing-user
                                                 (assoc existing-user :name "New Name" :email "new@test.com")))
                    db.user/update-user-profile (fn [_ user _] 
                                                  (reset! update-called user) 
                                                  1)]
        (let [result (controllers.user/update-user-profile profile-data user-id db)]
          (is (= "New Name" (:name result)))
          (is (= "new@test.com" (:email result)))
          (is (= "New Name" (:name @update-called)))
          (is (= "new@test.com" (:email @update-called))))))))

(deftest update-user-profile-throws-on-not-found
  (testing "Throws exception when user does not exist"
    (let [user-id 999
          profile-data {:name "New Name"}
          db (fn [] nil)]
      (with-redefs [db.user/find-user-by-id (fn [_ _] nil)]
        (is (thrown? clojure.lang.ExceptionInfo
                     (controllers.user/update-user-profile profile-data user-id db)))
        (try
          (controllers.user/update-user-profile profile-data user-id db)
          (is false "Should have thrown exception")
          (catch clojure.lang.ExceptionInfo e
            (is (= "User not found" (:message (ex-data e))))
            (is (= :not-found (:type (ex-data e))))))))))

(deftest update-user-profile-ignores-protected-fields
  (testing "Does not allow updating fields not in the profile schema"
    (let [user-id 1
          existing-user {:id 1 :name "User Name" :email "user@test.com" :password "hashed"}
          ;; Try to pass extra fields that shouldn't be in UserProfileUpdate
          profile-data {:name "New Name"}
          db (fn [] nil)
          find-called (atom 0)
          update-called (atom nil)]
      (with-redefs [db.user/find-user-by-id (fn [_ _] 
                                               (swap! find-called inc)
                                               (if (= @find-called 1)
                                                 existing-user
                                                 (assoc existing-user :name "New Name")))
                    db.user/update-user-profile (fn [_ user _] 
                                                  (reset! update-called user) 
                                                  1)]
        (let [result (controllers.user/update-user-profile profile-data user-id db)]
          ;; ID should remain unchanged
          (is (= 1 (:id result)))
          ;; Only name should be updated
          (is (= "New Name" (:name result))))))))

(deftest authorization-handler-rejects-other-users
  (testing "Handler validates that user can only update their own profile"
    (let [user-id-in-url "1"
          authenticated-user-id 2
          request {:params {:id user-id-in-url}
                   :identity {:id authenticated-user-id}
                   :body {:name "New Name"}
                   :database (fn [] nil)}
          response (api-peladaapp.handlers.user/update-profile request)]
      ;; Should return 403 Forbidden
      (is (= 403 (:status response)))
      (is (= "You can only update your own profile" (:body response))))))

(deftest authorization-handler-allows-own-profile
  (testing "Handler allows user to update their own profile"
    (let [user-id-in-url "1"
          authenticated-user-id 1
          existing-user {:id 1 :name "Old Name" :email "user@test.com" :password "hashed"}
          request {:params {:id user-id-in-url}
                   :identity {:id authenticated-user-id}
                   :body {:name "New Name"}
                   :database (fn [] nil)}
          find-called (atom 0)]
      (with-redefs [db.user/find-user-by-id (fn [_ _] 
                                               (swap! find-called inc)
                                               (if (= @find-called 1)
                                                 existing-user
                                                 (assoc existing-user :name "New Name")))
                    db.user/update-user-profile (fn [_ _ _] 1)]
        (let [response (api-peladaapp.handlers.user/update-profile request)]
          (is (= 200 (:status response)))
          (is (= "New Name" (-> response :body :name)))
          (is (= 1 (-> response :body :id))))))))
