(ns api-peladaapp.authorization-test
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.test :refer [deftest is testing]]))

(deftest test-get-user-id-from-request
  (testing "Extract user ID from request identity"
    (let [user-id (parse-uuid "00000000-0000-0000-0000-000000000123")
          request {:identity {:id user-id}}]
      (is (= user-id (auth/get-user-id-from-request request)))))

  (testing "Returns nil when identity is missing"
    (let [request {}]
      (is (nil? (auth/get-user-id-from-request request))))))

(deftest test-user-can-admin-organization?
  (let [user-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        org-id (parse-uuid "00000000-0000-0000-0000-000000000010")]
    (testing "Returns true when user is admin of organization"
      (let [mock-db (fn [] nil)
            result (with-redefs [db.admin/is-user-admin-of-organization?
                                 (fn [_ _ _] true)]
                     (auth/user-can-admin-organization? user-id org-id mock-db))]
        (is (true? result))))

    (testing "Returns false when user is not admin"
      (let [mock-db (fn [] nil)
            result (with-redefs [db.admin/is-user-admin-of-organization?
                                 (fn [_ _ _] false)]
                     (auth/user-can-admin-organization? user-id org-id mock-db))]
        (is (false? result))))))

(deftest test-user-is-in-organization?
  (let [user-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        org-id (parse-uuid "00000000-0000-0000-0000-000000000010")]
    (testing "Returns true when user is admin"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] true)
                      db.player/get-org-player-by-user-id
                      (fn [_ _ _] nil)]
          (is (true? (auth/user-is-in-organization? user-id org-id mock-db))))))

    (testing "Returns true when user is player"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] false)
                      db.player/get-org-player-by-user-id
                      (fn [_ _ _] {:id (parse-uuid "00000000-0000-0000-0000-000000000001")})]
          (is (true? (auth/user-is-in-organization? user-id org-id mock-db))))))

    (testing "Returns false when user is neither admin nor player"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] false)
                      db.player/get-org-player-by-user-id
                      (fn [_ _ _] nil)]
          (is (false? (auth/user-is-in-organization? user-id org-id mock-db))))))))

(deftest test-require-organization-admin!
  (let [user-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        org-id (parse-uuid "00000000-0000-0000-0000-000000000010")]
    (testing "Does not throw when user is admin"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] true)]
          (is (nil? (auth/require-organization-admin! user-id org-id mock-db))))))

    (testing "Throws exception when user is not admin"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] false)]
          (is (thrown-with-msg? clojure.lang.ExceptionInfo
                                #"User is not an admin of this organization"
                                (auth/require-organization-admin! user-id org-id mock-db))))))))

(deftest test-require-organization-member!
  (let [user-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        org-id (parse-uuid "00000000-0000-0000-0000-000000000010")]
    (testing "Does not throw when user is member"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] true)
                      db.player/get-org-player-by-user-id
                      (fn [_ _ _] nil)]
          (is (nil? (auth/require-organization-member! user-id org-id mock-db))))))

    (testing "Throws exception when user is not member"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] false)
                      db.player/get-org-player-by-user-id
                      (fn [_ _ _] nil)]
          (is (thrown-with-msg? clojure.lang.ExceptionInfo
                                #"User is not a member of this organization"
                                (auth/require-organization-member! user-id org-id mock-db))))))))

(deftest test-super-admin-bypass-authorization
  (let [user-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        org-id (parse-uuid "00000000-0000-0000-0000-000000000010")]
    (testing "Super Admin is allowed to admin organization even if not explicitly in OrganizationAdmins"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.user/find-user-by-id
                      (fn [_ _] {:id user-id :is-global-admin true})
                      db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] false)]
          (is (true? (auth/user-can-admin-organization? user-id org-id mock-db))))))

    (testing "Super Admin is considered a member of organization"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.user/find-user-by-id
                      (fn [_ _] {:id user-id :is-global-admin true})
                      db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] false)
                      db.player/get-org-player-by-user-id
                      (fn [_ _ _] nil)]
          (is (true? (auth/user-is-in-organization? user-id org-id mock-db))))))))
