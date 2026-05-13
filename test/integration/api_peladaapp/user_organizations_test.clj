(ns api-peladaapp.user-organizations-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest test-list-user-organizations
  (testing "Listing user organizations with roles"
    (let [app (-> th/*test-system* :app :app-handler)
          ds (api-peladaapp.test-helpers/get-test-datasource)

          ;; Register two users
          token1 (th/register-and-login! app {:name "User 1" :email "u1@test.com" :password "pass"})
          auth1 (th/auth-cookie token1)
          user1-id (th/user-id-by-email ds "u1@test.com")

          token2 (th/register-and-login! app {:name "User 2" :email "u2@test.com" :password "pass"})
          auth2 (th/auth-cookie token2)
          user2-id (th/user-id-by-email ds "u2@test.com")

          ;; 1. User 1 creates an organization
          ;; It should automatically make User 1 an admin AND a player
          resp (app (-> (mock/request :post "/api/organizations")
                        (mock/json-body {:name "Org 1"})
                        auth1))
          org1-id (parse-uuid (:id (th/decode-body resp)))]
      (is (= 201 (:status resp)))

      ;; Verify User 1 sees it in their organizations
      (let [list-resp (app (-> (mock/request :get (str "/api/users/" user1-id "/organizations")) auth1))
            orgs (th/decode-body list-resp)]
        (is (= 200 (:status list-resp)))
        (is (= 1 (count orgs)))
        (is (= "admin" (:role (first orgs))))
        (is (= "Org 1" (:name (first orgs)))))

      ;; 2. Admin (User 1) adds User 2 as a player to Org 1
      (let [add-player-resp (app (-> (mock/request :post "/api/players")
                                     (mock/json-body {:organization_id org1-id :user_id user2-id})
                                     auth1))]
        (is (= 201 (:status add-player-resp))))

      ;; Verify User 2 sees it as 'player'
      (let [list-resp (app (-> (mock/request :get (str "/api/users/" user2-id "/organizations")) auth2))
            orgs (th/decode-body list-resp)]
        (is (= 1 (count orgs)))
        (is (= "player" (:role (first orgs))))
        (is (= "Org 1" (:name (first orgs)))))

      ;; 3. User 2 creates their own organization
      (let [resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Org 2"})
                          auth2))]
        (is (= 201 (:status resp)))

        ;; Verify User 2 sees both organizations
        (let [list-resp (app (-> (mock/request :get (str "/api/users/" user2-id "/organizations")) auth2))
              orgs (th/decode-body list-resp)]
          (is (= 2 (count orgs)))
          (is (some #(and (= "Org 1" (:name %)) (= "player" (:role %))) orgs))
          (is (some #(and (= "Org 2" (:name %)) (= "admin" (:role %))) orgs))))

      ;; 4. Edge case: User with no organizations
      (let [token3 (th/register-and-login! app {:name "User 3" :email "u3@test.com" :password "pass"})
            auth3 (th/auth-cookie token3)
            user3-id (th/user-id-by-email ds "u3@test.com")
            list-resp (app (-> (mock/request :get (str "/api/users/" user3-id "/organizations")) auth3))]
        (is (= 200 (:status list-resp)))
        (is (= 0 (count (th/decode-body list-resp)))))

      ;; 5. Edge case: Non-existent user (should return 403 because it's not self and not global admin)
      (let [list-resp (app (-> (mock/request :get "/api/users/00000000-0000-0000-0000-000000009999/organizations") auth1))]
        (is (= 403 (:status list-resp)))))))
