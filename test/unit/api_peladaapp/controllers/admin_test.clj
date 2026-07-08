(ns api-peladaapp.controllers.admin-test
  (:require
   [api-peladaapp.controllers.admin :as admin.controller]
   [api-peladaapp.db.admin :as db.admin]
   [clojure.test :refer [deftest is testing]]))

(deftest test-add-organization-admin
  (let [admin-payload {:organization-id (random-uuid) :user-id (random-uuid)}
        dummy-admin (assoc admin-payload :id (random-uuid))
        db nil]
    (with-redefs [db.admin/insert-organization-admin (fn [payload _]
                                                       (is (= admin-payload payload))
                                                       (:id dummy-admin))
                  db.admin/get-organization-admin (fn [id _]
                                                    (is (= (:id dummy-admin) id))
                                                    dummy-admin)]
      (is (= dummy-admin (admin.controller/add-organization-admin admin-payload db))))))

(deftest test-get-organization-admin
  (let [uuid (random-uuid)
        dummy-admin {:id uuid :organization-id (random-uuid) :user-id (random-uuid)}
        db nil]
    (with-redefs [db.admin/get-organization-admin (fn [id _]
                                                    (is (= uuid id))
                                                    dummy-admin)]
      (is (= dummy-admin (admin.controller/get-organization-admin uuid db))))))

(deftest test-remove-organization-admin
  (let [uuid (random-uuid)
        org-uuid (random-uuid)
        user-uuid (random-uuid)
        dummy-admin {:id uuid :organization-id org-uuid :user-id user-uuid}
        db nil]
    (testing "when admin does not exist, returns 0"
      (with-redefs [db.admin/get-organization-admin (fn [_ _] nil)]
        (is (= 0 (admin.controller/remove-organization-admin uuid db)))))

    (testing "when admin is the last administrator, throws exception"
      (with-redefs [db.admin/get-organization-admin (fn [_ _] dummy-admin)
                    db.admin/list-admins-by-organization (fn [org-id _]
                                                           (is (= org-uuid org-id))
                                                           [dummy-admin])]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Cannot remove the last administrator"
             (admin.controller/remove-organization-admin uuid db)))))

    (testing "when there are other administrators, deletes and returns 1"
      (with-redefs [db.admin/get-organization-admin (fn [_ _] dummy-admin)
                    db.admin/list-admins-by-organization (fn [_ _] [dummy-admin {:id (random-uuid)}])
                    db.admin/delete-organization-admin (fn [id _]
                                                         (is (= uuid id))
                                                         1)]
        (is (= 1 (admin.controller/remove-organization-admin uuid db)))))))

(deftest test-remove-organization-admin-by-org-and-user
  (let [org-uuid (random-uuid)
        user-uuid (random-uuid)
        dummy-admin {:id (random-uuid) :organization-id org-uuid :user-id user-uuid}
        db nil]
    (testing "when admin is the last administrator, throws exception"
      (with-redefs [db.admin/list-admins-by-organization (fn [org-id _]
                                                           (is (= org-uuid org-id))
                                                           [dummy-admin])]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Cannot remove the last administrator"
             (admin.controller/remove-organization-admin-by-org-and-user org-uuid user-uuid db)))))

    (testing "when there are other administrators, deletes by org and user, and returns 1"
      (with-redefs [db.admin/list-admins-by-organization (fn [_ _] [dummy-admin {:id (random-uuid)}])
                    db.admin/delete-organization-admin-by-org-and-user (fn [org-id usr-id _]
                                                                         (is (= org-uuid org-id))
                                                                         (is (= user-uuid usr-id))
                                                                         1)]
        (is (= 1 (admin.controller/remove-organization-admin-by-org-and-user org-uuid user-uuid db)))))))

(deftest test-list-and-query-admins
  (let [org-uuid (random-uuid)
        user-uuid (random-uuid)
        dummy-admin {:id (random-uuid) :organization-id org-uuid :user-id user-uuid}
        db nil]
    (testing "list-organization-admins"
      (with-redefs [db.admin/list-admins-by-organization (fn [org-id _]
                                                           (is (= org-uuid org-id))
                                                           [dummy-admin])]
        (is (= [dummy-admin] (admin.controller/list-organization-admins org-uuid db)))))

    (testing "list-user-admin-organizations"
      (with-redefs [db.admin/list-organizations-by-admin (fn [usr-id _]
                                                           (is (= user-uuid usr-id))
                                                           [dummy-admin])]
        (is (= [dummy-admin] (admin.controller/list-user-admin-organizations user-uuid db)))))

    (testing "is-user-admin-of-organization?"
      (with-redefs [db.admin/is-user-admin-of-organization? (fn [usr-id org-id _]
                                                              (is (= user-uuid usr-id))
                                                              (is (= org-uuid org-id))
                                                              true)]
        (is (true? (admin.controller/is-user-admin-of-organization? user-uuid org-uuid db)))))))
