(ns api-peladaapp.handlers.global-admin-test
  (:require
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.handlers.global-admin :as handler.global-admin]
   [clojure.test :refer [deftest is testing]]))

(deftest test-list-organizations
  (let [db "dummy-db"
        mock-orgs {:data [{:id (random-uuid) :name "Org 1"}] :headers {"x-total-count" 1}}]
    (testing "without search query"
      (with-redefs [controller.organization/list-organizations (fn [_ _] mock-orgs)]
        (let [resp (handler.global-admin/list-organizations {:database db :query-params {}})]
          (is (= 200 (:status resp)))
          (is (= 1 (count (:body resp)))))))

    (testing "with search query q"
      (with-redefs [controller.organization/search-organizations (fn [_ q _]
                                                                   (is (= "test-query" q))
                                                                   mock-orgs)]
        (let [resp (handler.global-admin/list-organizations {:database db :query-params {"q" "test-query"}})]
          (is (= 200 (:status resp)))
          (is (= 1 (count (:body resp)))))))

    (testing "handles exceptions gracefully"
      (with-redefs [controller.organization/list-organizations (fn [_ _] (throw (Exception. "Failed list")))]
        (let [resp (handler.global-admin/list-organizations {:database db :query-params {}})]
          (is (= 500 (:status resp))))))))

(deftest test-toggle-organization-block
  (let [db "dummy-db"
        org-uuid (random-uuid)]
    (testing "when organization exists, toggles block state"
      (let [updated-flags (atom nil)]
        (with-redefs [db.organization/get-organization (fn [id _] (when (= id org-uuid) {:id org-uuid :is-blocked false}))
                      db.organization/update-organization-flags (fn [id flags _]
                                                                  (is (= org-uuid id))
                                                                  (reset! updated-flags flags))]
          (let [resp (handler.global-admin/toggle-organization-block {:database db :params {:id (str org-uuid)}})]
            (is (= 200 (:status resp)))
            (is (= true (:is_blocked (:body resp))))
            (is (= true (:is_blocked @updated-flags)))))))

    (testing "when organization not found, returns 404"
      (with-redefs [db.organization/get-organization (fn [_ _] nil)]
        (let [resp (handler.global-admin/toggle-organization-block {:database db :params {:id (str org-uuid)}})]
          (is (= 404 (:status resp))))))))

(deftest test-toggle-user-block
  (let [db "dummy-db"
        user-uuid (random-uuid)]
    (testing "when user exists, toggles block state"
      (let [updated-flags (atom nil)]
        (with-redefs [db.user/find-user-by-id (fn [id _] (when (= id user-uuid) {:id user-uuid :is-blocked false}))
                      db.user/update-user-flags (fn [id flags _]
                                                  (is (= user-uuid id))
                                                  (reset! updated-flags flags))]
          (let [resp (handler.global-admin/toggle-user-block {:database db :params {:id (str user-uuid)}})]
            (is (= 200 (:status resp)))
            (is (= true (:is_blocked (:body resp))))
            (is (= true (:is_blocked @updated-flags)))))))

    (testing "when user not found, returns 404"
      (with-redefs [db.user/find-user-by-id (fn [_ _] nil)]
        (let [resp (handler.global-admin/toggle-user-block {:database db :params {:id (str user-uuid)}})]
          (is (= 404 (:status resp))))))))

(deftest test-toggle-user-org-creation
  (let [db "dummy-db"
        user-uuid (random-uuid)]
    (testing "when user exists, toggles allow-org-creation state"
      (let [updated-flags (atom nil)]
        (with-redefs [db.user/find-user-by-id (fn [id _] (when (= id user-uuid) {:id user-uuid :allow-org-creation false}))
                      db.user/update-user-flags (fn [id flags _]
                                                  (is (= user-uuid id))
                                                  (reset! updated-flags flags))]
          (let [resp (handler.global-admin/toggle-user-org-creation {:database db :params {:id (str user-uuid)}})]
            (is (= 200 (:status resp)))
            (is (= true (:allow_org_creation (:body resp))))
            (is (= true (:allow_org_creation @updated-flags)))))))

    (testing "when user not found, returns 404"
      (with-redefs [db.user/find-user-by-id (fn [_ _] nil)]
        (let [resp (handler.global-admin/toggle-user-org-creation {:database db :params {:id (str user-uuid)}})]
          (is (= 404 (:status resp))))))))

(deftest test-toggle-user-global-admin
  (let [db "dummy-db"
        user-uuid (random-uuid)]
    (testing "when user exists, toggles is-global-admin state"
      (let [updated-flags (atom nil)]
        (with-redefs [db.user/find-user-by-id (fn [id _] (when (= id user-uuid) {:id user-uuid :is-global-admin false}))
                      db.user/update-user-flags (fn [id flags _]
                                                  (is (= user-uuid id))
                                                  (reset! updated-flags flags))]
          (let [resp (handler.global-admin/toggle-user-global-admin {:database db :params {:id (str user-uuid)}})]
            (is (= 200 (:status resp)))
            (is (= true (:is_super_admin (:body resp))))
            (is (= true (:is_super_admin @updated-flags)))))))

    (testing "when user not found, returns 404"
      (with-redefs [db.user/find-user-by-id (fn [_ _] nil)]
        (let [resp (handler.global-admin/toggle-user-global-admin {:database db :params {:id (str user-uuid)}})]
          (is (= 404 (:status resp))))))))

(deftest test-get-organization-feature-flags
  (let [db "dummy-db"
        org-uuid (random-uuid)]
    (testing "when feature flags exist, returns them"
      (with-redefs [db.organization/get-organization-feature-flags (fn [id _] (when (= id org-uuid) {:player_characteristics true}))]
        (let [resp (handler.global-admin/get-organization-feature-flags {:database db :params {:id (str org-uuid)}})]
          (is (= 200 (:status resp)))
          (is (= true (:player_characteristics (:body resp)))))))

    (testing "when flags do not exist but org does, inserts default flags and returns them"
      (let [flags-exist (atom false)
            inserted-defaults (atom false)]
        (with-redefs [db.organization/get-organization-feature-flags (fn [id _]
                                                                       (when (= id org-uuid)
                                                                         (if @flags-exist
                                                                           {:player_characteristics false}
                                                                           nil)))
                      db.organization/get-organization (fn [id _] (when (= id org-uuid) {:id org-uuid}))
                      db.organization/insert-default-feature-flags (fn [id _]
                                                                     (is (= org-uuid id))
                                                                     (reset! inserted-defaults true)
                                                                     (reset! flags-exist true))]
          (let [resp (handler.global-admin/get-organization-feature-flags {:database db :params {:id (str org-uuid)}})]
            (is (= 200 (:status resp)))
            (is @inserted-defaults)
            (is (= false (:player_characteristics (:body resp))))))))

    (testing "when organization not found, returns 404"
      (with-redefs [db.organization/get-organization-feature-flags (fn [_ _] nil)
                    db.organization/get-organization (fn [_ _] nil)]
        (let [resp (handler.global-admin/get-organization-feature-flags {:database db :params {:id (str org-uuid)}})]
          (is (= 404 (:status resp))))))))

(deftest test-update-organization-feature-flags
  (let [db "dummy-db"
        org-uuid (random-uuid)
        update-body {:player_characteristics true}]
    (testing "when organization exists, inserts defaults if missing and updates flags"
      (let [inserted-defaults (atom false)
            updated-flags (atom false)]
        (with-redefs [db.organization/get-organization (fn [id _] (when (= id org-uuid) {:id org-uuid}))
                      db.organization/get-organization-feature-flags (fn [id _]
                                                                       (when (= id org-uuid)
                                                                         (if @updated-flags
                                                                           {:player_characteristics true}
                                                                           nil)))
                      db.organization/insert-default-feature-flags (fn [id _]
                                                                     (is (= org-uuid id))
                                                                     (reset! inserted-defaults true))
                      db.organization/update-organization-feature-flags (fn [id body _]
                                                                          (is (= org-uuid id))
                                                                          (is (= update-body body))
                                                                          (reset! updated-flags true))]
          (let [resp (handler.global-admin/update-organization-feature-flags {:database db
                                                                              :params {:id (str org-uuid)}
                                                                              :body update-body})]
            (is (= 200 (:status resp)))
            (is @inserted-defaults)
            (is (= true (:player_characteristics (:body resp))))))))

    (testing "when organization not found, returns 404"
      (with-redefs [db.organization/get-organization (fn [_ _] nil)]
        (let [resp (handler.global-admin/update-organization-feature-flags {:database db
                                                                            :params {:id (str org-uuid)}
                                                                            :body update-body})]
          (is (= 404 (:status resp))))))))
