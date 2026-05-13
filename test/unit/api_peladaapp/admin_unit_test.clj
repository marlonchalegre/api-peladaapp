(ns api-peladaapp.admin-unit-test
  (:require
   [api-peladaapp.adapters.admin :as adapter.admin]
   [clojure.test :refer [deftest is testing]]))

(deftest test-admin-adapter
  (let [admin-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        org-id (parse-uuid "00000000-0000-0000-0000-000000000010")
        user-id (parse-uuid "00000000-0000-0000-0000-000000000020")]
    (testing "db->model adapter includes user details when present"
      (let [db-admin-with-user {:OrganizationAdmins/id admin-id
                                :OrganizationAdmins/organization_id org-id
                                :OrganizationAdmins/user_id user-id
                                :OrganizationAdmins/created_at "2025-10-29T10:00:00"
                                :user_name "John Doe"}
            result (adapter.admin/db->model db-admin-with-user)]
        (is (= admin-id (:id result)))
        (is (= org-id (:organization-id result)))
        (is (= user-id (:user-id result)))
        (is (= "John Doe" (:user-name result)))))

    (testing "db->model adapter includes organization name when present"
      (let [db-admin-with-org {:OrganizationAdmins/id admin-id
                               :OrganizationAdmins/organization_id org-id
                               :OrganizationAdmins/user_id user-id
                               :OrganizationAdmins/created_at "2025-10-29T10:00:00"
                               :organization_name "Test Org"}
            result (adapter.admin/db->model db-admin-with-org)]
        (is (= admin-id (:id result)))
        (is (= org-id (:organization-id result)))
        (is (= user-id (:user-id result)))
        (is (= "Test Org" (:organization-name result)))))

    (testing "db->model adapter includes all optional fields when present"
      (let [db-admin-full {:OrganizationAdmins/id admin-id
                           :OrganizationAdmins/organization_id org-id
                           :OrganizationAdmins/user_id user-id
                           :OrganizationAdmins/created_at "2025-10-29T10:00:00"
                           :user_name "Jane Smith"
                           :organization_name "Full Org"}
            result (adapter.admin/db->model db-admin-full)]
        (is (= admin-id (:id result)))
        (is (= org-id (:organization-id result)))
        (is (= user-id (:user-id result)))
        (is (= "Jane Smith" (:user-name result)))
        (is (= "Full Org" (:organization-name result)))))

    (testing "db->model adapter works without optional fields"
      (let [db-admin-minimal {:OrganizationAdmins/id admin-id
                              :OrganizationAdmins/organization_id org-id
                              :OrganizationAdmins/user_id user-id
                              :OrganizationAdmins/created_at "2025-10-29T10:00:00"}
            result (adapter.admin/db->model db-admin-minimal)]
        (is (= admin-id (:id result)))
        (is (= org-id (:organization-id result)))
        (is (= user-id (:user-id result)))
        (is (nil? (:user-name result)))
        (is (nil? (:organization-name result)))))

    (testing "db->model returns nil for nil input"
      (is (nil? (adapter.admin/db->model nil))))))
