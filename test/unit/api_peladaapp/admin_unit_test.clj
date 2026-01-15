(ns api-peladaapp.admin-unit-test
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [clojure.test :refer [deftest is testing]]))

(deftest test-admin-adapter
  (testing "db->model adapter includes user details when present"
    (let [db-admin-with-user {:OrganizationAdmins/id 1
                              :OrganizationAdmins/organization_id 10
                              :OrganizationAdmins/user_id 20
                              :OrganizationAdmins/created_at "2025-10-29T10:00:00"
                              :user_name "John Doe"
                              :user_email "john@example.com"}
          result (adapter.admin/db->model db-admin-with-user)]
      (is (= 1 (:id result)))
      (is (= 10 (:organization-id result)))
      (is (= 20 (:user-id result)))
      (is (= "John Doe" (:user-name result)))
      (is (= "john@example.com" (:user-email result)))))

  (testing "db->model adapter includes organization name when present"
    (let [db-admin-with-org {:OrganizationAdmins/id 1
                             :OrganizationAdmins/organization_id 10
                             :OrganizationAdmins/user_id 20
                             :OrganizationAdmins/created_at "2025-10-29T10:00:00"
                             :organization_name "Test Org"}
          result (adapter.admin/db->model db-admin-with-org)]
      (is (= 1 (:id result)))
      (is (= 10 (:organization-id result)))
      (is (= 20 (:user-id result)))
      (is (= "Test Org" (:organization-name result)))))

  (testing "db->model adapter includes all optional fields when present"
    (let [db-admin-full {:OrganizationAdmins/id 1
                         :OrganizationAdmins/organization_id 10
                         :OrganizationAdmins/user_id 20
                         :OrganizationAdmins/created_at "2025-10-29T10:00:00"
                         :user_name "Jane Smith"
                         :user_email "jane@example.com"
                         :organization_name "Full Org"}
          result (adapter.admin/db->model db-admin-full)]
      (is (= 1 (:id result)))
      (is (= 10 (:organization-id result)))
      (is (= 20 (:user-id result)))
      (is (= "Jane Smith" (:user-name result)))
      (is (= "jane@example.com" (:user-email result)))
      (is (= "Full Org" (:organization-name result)))))

  (testing "db->model adapter works without optional fields"
    (let [db-admin-minimal {:OrganizationAdmins/id 1
                            :OrganizationAdmins/organization_id 10
                            :OrganizationAdmins/user_id 20
                            :OrganizationAdmins/created_at "2025-10-29T10:00:00"}
          result (adapter.admin/db->model db-admin-minimal)]
      (is (= 1 (:id result)))
      (is (= 10 (:organization-id result)))
      (is (= 20 (:user-id result)))
      (is (nil? (:user-name result)))
      (is (nil? (:user-email result)))
      (is (nil? (:organization-name result)))))

  (testing "db->model returns nil for nil input"
    (is (nil? (adapter.admin/db->model nil)))))
