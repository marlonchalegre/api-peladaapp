(ns api-peladaapp.org-creation-permission-test
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest user-without-permission-cannot-create-org
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    ;; Register a user WITHOUT granting org creation permission
    (app (-> (mock/request :post "/auth/register")
             (mock/json-body {:name "No Perm User"
                              :email "noperm@test.com"
                              :password "pass123"})))
    (let [login (app (-> (mock/request :post "/auth/login")
                         (mock/json-body {:email "noperm@test.com" :password "pass123"})))
          token (:token (th/decode-body login))
          auth (th/auth-cookie token)]
      ;; Verify user exists in DB
      (is (some? (th/user-id-by-email ds "noperm@test.com")))
      ;; Attempt org creation without permission — must return 403
      (let [resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Forbidden Org"})
                          auth))]
        (is (= 403 (:status resp)))))))

(deftest user-with-permission-can-create-org
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    ;; Register user and explicitly grant org creation
    (app (-> (mock/request :post "/auth/register")
             (mock/json-body {:name "Perm User"
                              :email "perm@test.com"
                              :password "pass123"})))
    (let [login (app (-> (mock/request :post "/auth/login")
                         (mock/json-body {:email "perm@test.com" :password "pass123"})))
          token (:token (th/decode-body login))
          auth (th/auth-cookie token)
          _ (th/grant-org-creation! ds "perm@test.com")
          resp (app (-> (mock/request :post "/api/organizations")
                        (mock/json-body {:name "Allowed Org"})
                        auth))]
      (is (= 201 (:status resp))))))

(deftest blocked-org-prevents-pelada-creation
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    ;; Register user, grant org creation, create org
    (app (-> (mock/request :post "/auth/register")
             (mock/json-body {:name "Block Test User"
                              :email "blocktest@test.com"
                              :password "pass123"})))
    (let [login (app (-> (mock/request :post "/auth/login")
                         (mock/json-body {:email "blocktest@test.com" :password "pass123"})))
          token (:token (th/decode-body login))
          auth (th/auth-cookie token)
          _ (th/grant-org-creation! ds "blocktest@test.com")
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Block Me Org"})
                            auth))
          org-id (misc/as-uuid (:id (th/decode-body org-resp)))]
      (is (= 201 (:status org-resp)))
      ;; Block the organization via direct DB update
      (jdbc/execute! ds [(str "UPDATE \"Organizations\" SET is_blocked = true WHERE id = '" org-id "'")])
      ;; Attempt to create a pelada — must return 403 because org is blocked
      (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                 (mock/json-body {:organization_id org-id})
                                 auth))]
        (is (= 403 (:status pelada-resp)))))))

(deftest default-new-user-cannot-create-org
  ;; Regression test: after the allow_org_creation default changed to false,
  ;; a freshly registered user must NOT be able to create an org.
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    (app (-> (mock/request :post "/auth/register")
             (mock/json-body {:name "Brand New User"
                              :email "brandnew@test.com"
                              :password "pass123"})))
    (let [login (app (-> (mock/request :post "/auth/login")
                         (mock/json-body {:email "brandnew@test.com" :password "pass123"})))
          token (:token (th/decode-body login))
          auth (th/auth-cookie token)]
      ;; Sanity: user exists
      (is (some? (th/user-id-by-email ds "brandnew@test.com")))
      ;; Must be denied — no grant has been issued
      (let [resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Should Not Exist"})
                          auth))]
        (is (= 403 (:status resp))
            "Freshly registered user must not be able to create an org (allow_org_creation defaults to false)")))))
