(ns api-peladaapp.authorization-test
  (:require
   [api-peladaapp.db.admin :as db.admin]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.pelada :as db.pelada]
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
          (is (true? (auth/user-is-in-organization? user-id org-id mock-db))))))

    (testing "Handles database error in user lookup by defaulting user to nil"
      (let [mock-db (fn [] nil)]
        (with-redefs [db.user/find-user-by-id
                      (fn [_ _] (throw (RuntimeException. "DB error")))
                      db.admin/is-user-admin-of-organization?
                      (fn [_ _ _] false)]
          (is (false? (auth/user-can-admin-organization? user-id org-id mock-db))))))))

(deftest test-require-self-or-admin!
  (let [user-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        other-user-id (parse-uuid "00000000-0000-0000-0000-000000000002")]
    (testing "Allows request if current user matches target user"
      (is (nil? (auth/require-self-or-admin! {:identity {:id user-id}} user-id))))

    (testing "Allows request if current user is global admin"
      (is (nil? (auth/require-self-or-admin! {:identity {:id other-user-id :is-global-admin? true}} user-id))))

    (testing "Throws forbidden exception if user is neither target user nor global admin"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Forbidden"
                            (auth/require-self-or-admin! {:identity {:id other-user-id :is-global-admin? false}} user-id))))))

(deftest test-require-feature-flag!
  (let [org-id (parse-uuid "00000000-0000-0000-0000-000000000010")]
    (testing "Allows if database call throws (defaults to true)"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] (throw (RuntimeException. "DB error")))]
        (is (nil? (auth/require-feature-flag! org-id :finance_control {})))))

    (testing "Allows if feature flag is true"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:finance_control true})]
        (is (nil? (auth/require-feature-flag! org-id :finance_control {})))))

    (testing "Throws exception if feature flag is false"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:finance_control false})]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Feature finance_control is not enabled"
                              (auth/require-feature-flag! org-id :finance_control {})))))))

(deftest test-check-member-limit!
  (let [org-id (parse-uuid "00000000-0000-0000-0000-000000000010")]
    (testing "Allows if unlimited members flag is true"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:unlimited_members true})]
        (is (nil? (auth/check-member-limit! org-id {})))))

    (testing "Allows if count is less than 15"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:unlimited_members false})
                    db.player/count-players-by-org
                    (fn [_ _] 14)]
        (is (nil? (auth/check-member-limit! org-id {})))))

    (testing "Throws exception if count is 15 or more"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:unlimited_members false})
                    db.player/count-players-by-org
                    (fn [_ _] 15)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Limite de membros atingido"
                              (auth/check-member-limit! org-id {})))))

    (testing "Allows if database count call throws (defaults count to 0)"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:unlimited_members false})
                    db.player/count-players-by-org
                    (fn [_ _] (throw (RuntimeException. "DB error")))]
        (is (nil? (auth/check-member-limit! org-id {})))))))

(deftest test-check-pelada-limit!
  (let [org-id (parse-uuid "00000000-0000-0000-0000-000000000010")]
    (testing "Allows if unlimited peladas flag is true"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:unlimited_peladas true})]
        (is (nil? (auth/check-pelada-limit! org-id {})))))

    (testing "Allows if count is less than 2"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:unlimited_peladas false})
                    db.pelada/count-peladas-in-month-by-org
                    (fn [_ _ _ _] 1)]
        (is (nil? (auth/check-pelada-limit! org-id {})))))

    (testing "Throws exception if count is 2 or more"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:unlimited_peladas false})
                    db.pelada/count-peladas-in-month-by-org
                    (fn [_ _ _ _] 2)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Limite de peladas atingido"
                              (auth/check-pelada-limit! org-id {})))))

    (testing "Allows if database count call throws (defaults count to 0)"
      (with-redefs [api-peladaapp.db.organization/get-organization-feature-flags
                    (fn [_ _] {:unlimited_peladas false})
                    db.pelada/count-peladas-in-month-by-org
                    (fn [_ _ _ _] (throw (RuntimeException. "DB error")))]
        (is (nil? (auth/check-pelada-limit! org-id {})))))))

