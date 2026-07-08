(ns api-peladaapp.user-unit-test
  (:require
   [api-peladaapp.controllers.user :as controller.user]
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.user :as logic.user]
   [buddy.hashers]
   [buddy.sign.jwt]
   [clojure.test :refer [deftest is testing]]))

(deftest create-user-test
  (let [db (fn [] nil)
        new-user {:name "New User" :username "newuser" :email "new@e.com" :password "pass"}
        user-no-email {:name "No Email" :username "noemail" :password "pass"}
        uuid-100 (parse-uuid "00000000-0000-0000-0000-000000000100")
        uuid-101 (parse-uuid "00000000-0000-0000-0000-000000000101")
        uuid-1   (parse-uuid "00000000-0000-0000-0000-000000000001")]

    (testing "Create user with all fields"
      (with-redefs [db.user/find-user-by-identifier (fn [_ _] nil)
                    db.user/find-user-by-email (fn [_ _] nil)
                    db.user/insert-user (fn [_ _] uuid-100)
                    logic.user/encrypt-password (fn [u] u)]
        (let [result (controller.user/create-user new-user db)]
          (is (= uuid-100 (:id result)))
          (is (= "newuser" (:username result))))))

    (testing "Create user without email"
      (with-redefs [db.user/find-user-by-identifier (fn [_ _] nil)
                    db.user/find-user-by-email (fn [_ _] nil)
                    db.user/insert-user (fn [_ _] uuid-101)
                    logic.user/encrypt-password (fn [u] u)]
        (let [result (controller.user/create-user user-no-email db)]
          (is (= uuid-101 (:id result)))
          (is (nil? (:email result))))))

    (testing "Fails if username already exists"
      (with-redefs [db.user/find-user-by-identifier (fn [_ _] {:id uuid-1 :username "newuser" :password "hash"})
                    db.user/find-user-by-email (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Username already exists"
                              (controller.user/create-user new-user db)))))

    (testing "Fails if email already exists"
      (with-redefs [db.user/find-user-by-identifier (fn [_ _] nil)
                    db.user/find-user-by-email (fn [_ _] {:id uuid-1 :email "new@e.com" :password "hash"})]
        (is (thrown-with-msg? Exception #"Email already exists"
                              (controller.user/create-user new-user db)))))))

(deftest test-encrypt-password
  (testing "Encrypts password if user map contains :password"
    (let [user {:password "plaintext-pass"}
          encrypted (logic.user/encrypt-password user)]
      (is (contains? encrypted :password))
      (is (not= "plaintext-pass" (:password encrypted)))
      (is (buddy.hashers/check "plaintext-pass" (:password encrypted)))))

  (testing "Returns user map unchanged if :password is not present"
    (let [user {:username "user-no-pass"}]
      (is (= user (logic.user/encrypt-password user))))))

(deftest test-build-token
  (let [user-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        secret "testsecret123456789012345678901234567890"]
    (testing "Builds a signed token with all fields and defaults admin-orgs when nil"
      (let [user {:id user-id :email "u@e.com" :phone "123" :avatar-filename "avatar.png" :is-global-admin true}
            token (logic.user/build-token user secret)
            decoded (buddy.sign.jwt/unsign token secret {:alg :hs512})]
        (is (= (str user-id) (:id decoded)))
        (is (= "u@e.com" (:email decoded)))
        (is (= "123" (:phone decoded)))
        (is (= "avatar.png" (:avatar_filename decoded)))
        (is (true? (:admin? decoded)))
        (is (true? (:is-global-admin? decoded)))
        (is (= [] (:admin_orgs decoded)))))

    (testing "Uses provided admin-orgs and handles false is-global-admin"
      (let [org-id (parse-uuid "00000000-0000-0000-0000-000000000002")
            user {:id user-id :email "u2@e.com" :phone nil :avatar-filename nil :is-global-admin false :admin-orgs [org-id]}
            token (logic.user/build-token user secret)
            decoded (buddy.sign.jwt/unsign token secret {:alg :hs512})]
        (is (= (str user-id) (:id decoded)))
        (is (false? (:admin? decoded)))
        (is (false? (:is-global-admin? decoded)))
        (is (= [(str org-id)] (:admin_orgs decoded)))))))

(deftest test-create-user-partial-claim
  (let [db "dummy-db"
        user-id (random-uuid)
        new-user {:email "partial@e.com" :username "partial" :password "pass"}]
    (testing "updates/claims partial user (exists without password)"
      (with-redefs [db.user/find-user-by-email (fn [email _]
                                                 (is (= "partial@e.com" email))
                                                 {:id user-id :email "partial@e.com"}) ; no :password key
                    db.user/find-user-by-identifier (fn [_ _] nil)
                    db.user/update-user (fn [id user-data _]
                                          (is (= user-id id))
                                          (is (some? (:password user-data)))
                                          1)]
        (let [resp (controller.user/create-user new-user db)]
          (is (= user-id (:id resp))))))))

(deftest test-update-user-controller
  (let [db "dummy-db"
        user-id (random-uuid)
        update-data {:name "Jane"}]
    (testing "updates user profile successfully"
      (let [user-state (atom {:id user-id :name "John" :email "john@e.com"})]
        (with-redefs [db.user/find-user-by-id (fn [id _]
                                                (is (= user-id id))
                                                @user-state)
                      db.user/update-user (fn [id data _]
                                            (is (= user-id id))
                                            (is (= "Jane" (:name data)))
                                            (swap! user-state merge data)
                                            1)]
          (let [resp (controller.user/update-user update-data user-id db)]
            (is (= "Jane" (:name resp)))))))

    (testing "throws not found when user does not exist"
      (with-redefs [db.user/find-user-by-id (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"User not found"
                              (controller.user/update-user update-data user-id db)))))))

(deftest test-get-user-controller
  (let [db "dummy-db"
        user-id (random-uuid)
        mock-user {:id user-id :name "John"}]
    (testing "gets user successfully with stats and admin-orgs"
      (with-redefs [db.user/find-user-by-id (fn [id _]
                                              (is (= user-id id))
                                              mock-user)
                    api-peladaapp.db.admin/list-organizations-by-admin (fn [id _]
                                                                         (is (= user-id id))
                                                                         [{:organization-id "org-1"}])
                    db.user/get-user-stats (fn [id _]
                                             (is (= user-id id))
                                             {:stats "ok"})]
        (let [resp (controller.user/get-user user-id db)]
          (is (= "org-1" (first (:admin-orgs resp))))
          (is (= {:stats "ok"} (:stats resp))))))

    (testing "throws not found when user does not exist"
      (with-redefs [db.user/find-user-by-id (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"User not found"
                              (controller.user/get-user user-id db)))))))

(deftest test-delete-user-controller
  (let [db "dummy-db"
        user-id (random-uuid)]
    (testing "deletes user successfully when exists"
      (with-redefs [db.user/find-user-by-id (fn [id _] {:id id})
                    db.user/delete-user (fn [id _]
                                          (is (= user-id id))
                                          1)]
        (is (= 1 (controller.user/delete-user user-id db)))))

    (testing "throws not found when user does not exist"
      (with-redefs [db.user/find-user-by-id (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"User not found"
                              (controller.user/delete-user user-id db)))))))

(deftest test-lists-and-searches
  (let [db "dummy-db"
        user-id (random-uuid)]
    (testing "list-users"
      (with-redefs [db.user/list-users (fn [_ offset limit]
                                         (is (= 0 offset))
                                         (is (= 20 limit))
                                         [{:id 1}])
                    db.user/count-users (fn [_] 1)]
        (let [resp (controller.user/list-users db {})]
          (is (= "1" (get-in resp [:headers "X-Total"]))))))

    (testing "search-users"
      (with-redefs [db.user/search-users (fn [_ q offset limit]
                                           (is (= "query" q))
                                           (is (= 20 offset))
                                           (is (= 10 limit))
                                           [{:id 1}])
                    db.user/count-searched-users (fn [_ q]
                                                   (is (= "query" q))
                                                   1)]
        (let [resp (controller.user/search-users db "query" {:page 3 :per-page 10})]
          (is (= "1" (get-in resp [:headers "X-Total"]))))))

    (testing "search-users-in-shared-orgs"
      (with-redefs [db.user/search-users-in-shared-orgs (fn [_ c-id _q _offset _limit]
                                                          (is (= user-id c-id))
                                                          [{:id 1}])
                    db.user/count-searched-users-in-shared-orgs (fn [_ _c-id _q] 1)]
        (let [resp (controller.user/search-users-in-shared-orgs db user-id "q" {})]
          (is (= "1" (get-in resp [:headers "X-Total"]))))))))

(deftest test-update-user-profile-controller
  (let [db "dummy-db"
        user-id (random-uuid)
        other-user-id (random-uuid)
        mock-user {:id user-id :name "John" :username "john" :email "john@e.com"}]
    (testing "throws not found if user missing"
      (with-redefs [db.user/find-user-by-id (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"User not found"
                              (controller.user/update-user-profile {} user-id db)))))

    (testing "throws if username belongs to someone else"
      (with-redefs [db.user/find-user-by-id (fn [_ _] mock-user)
                    db.user/find-user-by-username (fn [username _]
                                                    (is (= "jane" username))
                                                    {:id other-user-id :username "jane"})]
        (is (thrown-with-msg? Exception #"Username already exists"
                              (controller.user/update-user-profile {:username "jane"} user-id db)))))

    (testing "throws if email belongs to someone else"
      (with-redefs [db.user/find-user-by-id (fn [_ _] mock-user)
                    db.user/find-user-by-username (fn [_ _] nil)
                    db.user/find-user-by-email (fn [email _]
                                                 (is (= "jane@e.com" email))
                                                 {:id other-user-id :email "jane@e.com"})]
        (is (thrown-with-msg? Exception #"Email already exists"
                              (controller.user/update-user-profile {:email "jane@e.com"} user-id db)))))

    (testing "saves successfully with password encryption and phone/avatar updates"
      (let [updated-user-profile (atom nil)]
        (with-redefs [db.user/find-user-by-id (fn [_id _]
                                                (if @updated-user-profile
                                                  (assoc mock-user :phone "123" :avatar-filename "av.png")
                                                  mock-user))
                      db.user/find-user-by-username (fn [_ _] nil)
                      db.user/find-user-by-email (fn [_ _] nil)
                      db.admin/list-organizations-by-admin (fn [_ _] [])
                      db.user/get-user-stats (fn [_ _] {})
                      db.user/update-user-profile (fn [id profile _]
                                                    (is (= user-id id))
                                                    (reset! updated-user-profile profile)
                                                    1)]
          (let [resp (controller.user/update-user-profile {:email ""
                                                           :phone "123"
                                                           :avatar-filename "av.png"
                                                           :password "newpass"}
                                                          user-id db)]
            (is (= "123" (:phone resp)))
            (is (= "av.png" (:avatar-filename resp)))
            ;; Email is blank -> mapped to nil
            (is (nil? (:email @updated-user-profile)))))))))


