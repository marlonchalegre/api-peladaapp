(ns api-peladaapp.global-admin-pelada-test
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest global-admin-pelada-management-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)

        ;; 1. Register a normal user and a global admin user
        _ (app (-> (mock/request :post "/auth/register")
                   (mock/json-body {:name "Normal User"
                                    :email "user@test.com"
                                    :password "pass123"
                                    :username "normaluser"})))
        login-resp1 (app (-> (mock/request :post "/auth/login")
                             (mock/json-body {:email "user@test.com" :password "pass123"})))
        user-token (:token (th/decode-body login-resp1))

        ;; Ensure allow_org_creation is true for test
        user-id (th/user-id-by-email ds "user@test.com")
        _ (jdbc/execute! ds [(str "UPDATE \"Users\" SET allow_org_creation = true WHERE id = '" user-id "'")])

        _ (app (-> (mock/request :post "/auth/register")
                   (mock/json-body {:name "Admin User"
                                    :email "admin@test.com"
                                    :password "pass123"
                                    :username "adminuser"})))
        _ (jdbc/execute! ds ["UPDATE \"Users\" SET is_super_admin = TRUE WHERE email = ?" "admin@test.com"])
        login-resp2 (app (-> (mock/request :post "/auth/login")
                             (mock/json-body {:email "admin@test.com" :password "pass123"})))

        admin-token (:token (th/decode-body login-resp2))

        ;; Create an organization and a pelada as the normal user
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Test Org"})
                          (th/auth-cookie user-token)))
        org-id (misc/as-uuid (:id (th/decode-body org-resp)))
        pelada-resp (app (-> (mock/request :post "/api/peladas")
                             (mock/json-body {:organization_id org-id
                                              :num_teams 2
                                              :players_per_team 5})
                             (th/auth-cookie user-token)))
        pelada-id (misc/as-uuid (:id (th/decode-body pelada-resp)))]

    (is (= 201 (:status pelada-resp)))

    ;; 2. Normal user cannot list all peladas (GET /api/admin/peladas)
    (let [list-resp (app (-> (mock/request :get "/api/admin/peladas")
                             (th/auth-cookie user-token)))]
      (is (= 403 (:status list-resp))))

    ;; 3. Normal user cannot delete pelada via admin endpoint (DELETE /api/admin/peladas/:id)
    (let [del-resp (app (-> (mock/request :delete (str "/api/admin/peladas/" pelada-id))
                            (th/auth-cookie user-token)))]
      (is (= 403 (:status del-resp))))

    ;; 4. Global admin can list all peladas (GET /api/admin/peladas)
    (let [list-resp (app (-> (mock/request :get "/api/admin/peladas")
                             (th/auth-cookie admin-token)))
          body (th/decode-body list-resp)
          headers (:headers list-resp)]
      (is (= 200 (:status list-resp)))
      (is (sequential? body))
      (is (= 1 (count body)))
      (is (= (str pelada-id) (:id (first body))))
      (is (= "Test Org" (:organization_name (first body))))
      (is (= "1" (get headers "X-Total")))
      (is (= "1" (get headers "X-Total-Pages"))))

    ;; Let's add some related items to verify cascade (e.g. teams are created automatically)
    (let [teams-count (:count (jdbc/execute-one! ds ["SELECT COUNT(*) as count FROM \"Teams\" WHERE pelada_id = ?" pelada-id]))]
      (is (= 2 teams-count)))

    ;; 5. Global admin can delete pelada (DELETE /api/admin/peladas/:id)
    (let [del-resp (app (-> (mock/request :delete (str "/api/admin/peladas/" pelada-id))
                            (th/auth-cookie admin-token)))]
      (is (= 200 (:status del-resp)))
      ;; Verify pelada is deleted from DB
      (is (nil? (jdbc/execute-one! ds ["SELECT id FROM \"Peladas\" WHERE id = ?" pelada-id])))
      ;; Verify that related teams are cascaded and deleted
      (is (= 0 (:count (jdbc/execute-one! ds ["SELECT COUNT(*) as count FROM \"Teams\" WHERE pelada_id = ?" pelada-id])))))

    ;; 6. Regression test: Delete pelada as organization admin via user endpoint, verifying matches and match substitutions are cascade deleted
    (let [admin-user-id (th/user-id-by-email ds "admin@test.com")
          ;; Add admin to org players
          _ (jdbc/execute! ds ["INSERT INTO \"OrganizationPlayers\" (organization_id, user_id) VALUES (?, ?)" org-id admin-user-id])
          player1-id (first (vals (jdbc/execute-one! ds ["SELECT id FROM \"OrganizationPlayers\" WHERE organization_id = ? AND user_id = ?" org-id user-id])))
          player2-id (first (vals (jdbc/execute-one! ds ["SELECT id FROM \"OrganizationPlayers\" WHERE organization_id = ? AND user_id = ?" org-id admin-user-id])))

          ;; Create a new pelada
          new-pelada-resp (app (-> (mock/request :post "/api/peladas")
                                   (mock/json-body {:organization_id org-id
                                                    :num_teams 2
                                                    :players_per_team 5})
                                   (th/auth-cookie user-token)))
          new-pelada-id (misc/as-uuid (:id (th/decode-body new-pelada-resp)))

          ;; Insert a match for this pelada
          teams (jdbc/execute! ds ["SELECT id FROM \"Teams\" WHERE pelada_id = ?" new-pelada-id])
          home-team-id (first (vals (first teams)))
          away-team-id (first (vals (second teams)))
          match-id (java.util.UUID/randomUUID)
          _ (jdbc/execute! ds ["INSERT INTO \"Matches\" (id, pelada_id, home_team_id, away_team_id, sequence) VALUES (?, ?, ?, ?, 1)"
                               match-id new-pelada-id home-team-id away-team-id])

          ;; Insert a match substitution for this match
          sub-id (java.util.UUID/randomUUID)
          _ (jdbc/execute! ds ["INSERT INTO \"MatchSubstitutions\" (id, match_id, out_player_id, in_player_id) VALUES (?, ?, ?, ?)"
                               sub-id match-id player1-id player2-id])

          ;; Deletion call via user endpoint
          user-del-resp (app (-> (mock/request :delete (str "/api/peladas/" new-pelada-id))
                                 (th/auth-cookie user-token)))]
      (is (= 200 (:status user-del-resp)))
      ;; Verify pelada is deleted
      (is (nil? (jdbc/execute-one! ds ["SELECT id FROM \"Peladas\" WHERE id = ?" new-pelada-id])))
      ;; Verify match is cascade-deleted
      (is (nil? (jdbc/execute-one! ds ["SELECT id FROM \"Matches\" WHERE id = ?" match-id])))
      ;; Verify match substitution is cascade-deleted
      (is (nil? (jdbc/execute-one! ds ["SELECT id FROM \"MatchSubstitutions\" WHERE id = ?" sub-id]))))))
