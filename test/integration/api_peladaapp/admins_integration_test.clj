(ns api-peladaapp.admins-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [api-peladaapp.test-helpers :as th]
            [next.jdbc :as jdbc]))

(deftest test-organization-admin-workflow
  (testing "Complete admin workflow: create org, add admin, list admins, remove admin"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
          
          ;; Register and login first user
          token1 (th/register-and-login! app {:name "Admin User" :email "admin@test.com" :password "pass123"})
          auth1 (th/auth-header token1)
          user1-id (th/user-id-by-email ds "admin@test.com")
          
          ;; Register second user
          _ (app (-> (mock/request :post "/auth/register") 
                    (mock/json-body {:name "Second User" :email "user2@test.com" :password "pass123"})))
          user2-id (th/user-id-by-email ds "user2@test.com")
          
          ;; Create organization (user1 becomes admin automatically)
          create-org-resp (app (-> (mock/request :post "/api/organizations") 
                                  (mock/json-body {:name "Test Org"}) 
                                  auth1))
          org (th/decode-body create-org-resp)
          org-id (:id org)]
      
      ;; Verify organization was created
      (is (= 201 (:status create-org-resp)))
      (is (some? org-id))
      
      ;; List admins - should have creator as admin with user details
      (let [list-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/admins")) auth1))
            admins (th/decode-body list-resp)
            first-admin (first admins)]
        (is (= 200 (:status list-resp)))
        (is (= 1 (count admins)))
        (is (= user1-id (:user_id first-admin)))
        ;; Verify user details are included
        (is (= "Admin User" (:user_name first-admin)))
        (is (= "admin@test.com" (:user_email first-admin))))
      
      ;; Add second user as admin
      (let [add-resp (app (-> (mock/request :post (str "/api/organizations/" org-id "/admins"))
                             (mock/json-body {:user_id user2-id})
                             auth1))]
        (is (= 201 (:status add-resp))))
      
      ;; List admins again - should have 2 admins with user details
      (let [list-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/admins")) auth1))
            admins (th/decode-body list-resp)
            admin1 (first (filter #(= user1-id (:user_id %)) admins))
            admin2 (first (filter #(= user2-id (:user_id %)) admins))]
        (is (= 200 (:status list-resp)))
        (is (= 2 (count admins)))
        (is (some #(= user1-id (:user_id %)) admins))
        (is (some #(= user2-id (:user_id %)) admins))
        ;; Verify user details for both admins
        (is (= "Admin User" (:user_name admin1)))
        (is (= "admin@test.com" (:user_email admin1)))
        (is (= "Second User" (:user_name admin2)))
        (is (= "user2@test.com" (:user_email admin2))))
      
      ;; Check if user1 is admin
      (let [check-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/users/" user1-id "/is-admin")) auth1))
            result (th/decode-body check-resp)]
        (is (= 200 (:status check-resp)))
        (is (true? (:is_admin result))))
      
      ;; Remove user2 as admin
      (let [remove-resp (app (-> (mock/request :delete (str "/api/organizations/" org-id "/admins/" user2-id)) auth1))]
        (is (= 200 (:status remove-resp))))
      
      ;; List admins - should be back to 1
      (let [list-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/admins")) auth1))
            admins (th/decode-body list-resp)]
        (is (= 200 (:status list-resp)))
        (is (= 1 (count admins)))
        (is (= user1-id (:user_id (first admins)))))
      
      ;; List organizations where user1 is admin - should include org name
      (let [orgs-resp (app (-> (mock/request :get (str "/api/users/" user1-id "/admin-organizations")) auth1))
            orgs (th/decode-body orgs-resp)
            first-org (first orgs)]
        (is (= 200 (:status orgs-resp)))
        (is (= 1 (count orgs)))
        (is (= org-id (:organization_id first-org)))
        (is (= "Test Org" (:organization_name first-org)))))))

(deftest test-pelada-authorization
  (testing "Only admins can create peladas"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
          
          ;; Register admin user
          token-admin (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
          auth-admin (th/auth-header token-admin)
          admin-id (th/user-id-by-email ds "admin@test.com")
          
          ;; Register regular user
          token-user (th/register-and-login! app {:name "User" :email "user@test.com" :password "pass"})
          auth-user (th/auth-header token-user)
          
          ;; Create organization (admin becomes admin)
          create-org-resp (app (-> (mock/request :post "/api/organizations") 
                                  (mock/json-body {:name "Test Org"}) 
                                  auth-admin))
          org-id (:id (th/decode-body create-org-resp))]
      
      ;; Admin can create pelada
      (let [create-pelada-resp (app (-> (mock/request :post "/api/peladas")
                                       (mock/json-body {:organization_id org-id 
                                                       :num_teams 2
                                                       :players_per_team 5})
                                       auth-admin))]
        (is (= 201 (:status create-pelada-resp))))
      
      ;; Regular user cannot create pelada (should be forbidden)
      (let [create-pelada-resp (app (-> (mock/request :post "/api/peladas")
                                       (mock/json-body {:organization_id org-id 
                                                       :num_teams 2
                                                       :players_per_team 5})
                                       auth-user))]
        (is (or (= 403 (:status create-pelada-resp))
                (= 500 (:status create-pelada-resp))))))))

(deftest test-authorization-logic
  (testing "Authorization functions work correctly"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
          
          token1 (th/register-and-login! app {:name "User1" :email "u1@test.com" :password "p"})
          auth1 (th/auth-header token1)
          user1-id (th/user-id-by-email ds "u1@test.com")
          
          token2 (th/register-and-login! app {:name "User2" :email "u2@test.com" :password "p"})
          auth2 (th/auth-header token2)
          user2-id (th/user-id-by-email ds "u2@test.com")
          
          ;; User1 creates org
          create-resp (app (-> (mock/request :post "/api/organizations") 
                              (mock/json-body {:name "Org1"}) 
                              auth1))
          org-id (:id (th/decode-body create-resp))]
      
      ;; User1 is admin, can check
      (let [check1 (app (-> (mock/request :get (str "/api/organizations/" org-id "/users/" user1-id "/is-admin")) auth1))]
        (is (= 200 (:status check1)))
        (is (true? (:is_admin (th/decode-body check1)))))
      
      ;; User2 is not admin
      (let [check2 (app (-> (mock/request :get (str "/api/organizations/" org-id "/users/" user2-id "/is-admin")) auth2))]
        (is (= 200 (:status check2)))
        (is (false? (:is_admin (th/decode-body check2))))))))

(deftest test-multiple-organizations-and-admins
  (testing "User can be admin of multiple organizations and organizations can have multiple admins"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
          
          ;; Register three users
          token1 (th/register-and-login! app {:name "Alice" :email "alice@test.com" :password "pass"})
          auth1 (th/auth-header token1)
          user1-id (th/user-id-by-email ds "alice@test.com")
          
          token2 (th/register-and-login! app {:name "Bob" :email "bob@test.com" :password "pass"})
          auth2 (th/auth-header token2)
          user2-id (th/user-id-by-email ds "bob@test.com")
          
          _ (th/register-and-login! app {:name "Charlie" :email "charlie@test.com" :password "pass"})
          user3-id (th/user-id-by-email ds "charlie@test.com")
          
          ;; User1 creates two organizations
          org1-resp (app (-> (mock/request :post "/api/organizations") 
                            (mock/json-body {:name "Organization Alpha"}) 
                            auth1))
          org1-id (:id (th/decode-body org1-resp))
          
          org2-resp (app (-> (mock/request :post "/api/organizations") 
                            (mock/json-body {:name "Organization Beta"}) 
                            auth1))
          org2-id (:id (th/decode-body org2-resp))
          
          ;; User2 creates one organization
          org3-resp (app (-> (mock/request :post "/api/organizations") 
                            (mock/json-body {:name "Organization Gamma"}) 
                            auth2))
          org3-id (:id (th/decode-body org3-resp))]
      
      ;; Add User2 as admin to Org1
      (app (-> (mock/request :post (str "/api/organizations/" org1-id "/admins"))
              (mock/json-body {:user_id user2-id})
              auth1))
      
      ;; Add User3 as admin to Org1
      (app (-> (mock/request :post (str "/api/organizations/" org1-id "/admins"))
              (mock/json-body {:user_id user3-id})
              auth1))
      
      ;; Add User1 as admin to Org3
      (app (-> (mock/request :post (str "/api/organizations/" org3-id "/admins"))
              (mock/json-body {:user_id user1-id})
              auth2))
      
      ;; Verify Org1 has 3 admins with correct user details
      (let [admins-resp (app (-> (mock/request :get (str "/api/organizations/" org1-id "/admins")) auth1))
            admins (th/decode-body admins-resp)]
        (is (= 200 (:status admins-resp)))
        (is (= 3 (count admins)))
        (is (some #(and (= user1-id (:user_id %)) 
                       (= "Alice" (:user_name %))
                       (= "alice@test.com" (:user_email %))) admins))
        (is (some #(and (= user2-id (:user_id %)) 
                       (= "Bob" (:user_name %))
                       (= "bob@test.com" (:user_email %))) admins))
        (is (some #(and (= user3-id (:user_id %)) 
                       (= "Charlie" (:user_name %))
                       (= "charlie@test.com" (:user_email %))) admins)))
      
      ;; Verify Org2 has only User1 as admin
      (let [admins-resp (app (-> (mock/request :get (str "/api/organizations/" org2-id "/admins")) auth1))
            admins (th/decode-body admins-resp)]
        (is (= 200 (:status admins-resp)))
        (is (= 1 (count admins)))
        (is (= user1-id (:user_id (first admins))))
        (is (= "Alice" (:user_name (first admins)))))
      
      ;; Verify Org3 has User1 and User2 as admins
      (let [admins-resp (app (-> (mock/request :get (str "/api/organizations/" org3-id "/admins")) auth2))
            admins (th/decode-body admins-resp)]
        (is (= 200 (:status admins-resp)))
        (is (= 2 (count admins)))
        (is (some #(and (= user1-id (:user_id %)) (= "Alice" (:user_name %))) admins))
        (is (some #(and (= user2-id (:user_id %)) (= "Bob" (:user_name %))) admins)))
      
      ;; Verify User1 is admin of 3 organizations with correct org names
      (let [orgs-resp (app (-> (mock/request :get (str "/api/users/" user1-id "/admin-organizations")) auth1))
            orgs (th/decode-body orgs-resp)]
        (is (= 200 (:status orgs-resp)))
        (is (= 3 (count orgs)))
        (is (some #(and (= org1-id (:organization_id %)) 
                       (= "Organization Alpha" (:organization_name %))) orgs))
        (is (some #(and (= org2-id (:organization_id %)) 
                       (= "Organization Beta" (:organization_name %))) orgs))
        (is (some #(and (= org3-id (:organization_id %)) 
                       (= "Organization Gamma" (:organization_name %))) orgs)))
      
      ;; Verify User2 is admin of 2 organizations
      (let [orgs-resp (app (-> (mock/request :get (str "/api/users/" user2-id "/admin-organizations")) auth2))
            orgs (th/decode-body orgs-resp)]
        (is (= 200 (:status orgs-resp)))
        (is (= 2 (count orgs)))
        (is (some #(and (= org1-id (:organization_id %)) 
                       (= "Organization Alpha" (:organization_name %))) orgs))
        (is (some #(and (= org3-id (:organization_id %)) 
                       (= "Organization Gamma" (:organization_name %))) orgs))))))
