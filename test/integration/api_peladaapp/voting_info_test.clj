(ns api-peladaapp.voting-info-test
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest voting-info-retention-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})

        ;; 1. Set up Organization and Pelada
        token1 (th/register-and-login! app {:name "User 1" :email "u1@test.com" :password "pass"})
        auth1 (th/auth-header token1)
        token2 (th/register-and-login! app {:name "User 2" :email "u2@test.com" :password "pass"})
        auth2 (th/auth-header token2)
        token3 (th/register-and-login! app {:name "User 3" :email "u3@test.com" :password "pass"})
        auth3 (th/auth-header token3)

        ;; User 1 creates org
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Voting Org"})
                                             auth1))))]

    ;; Add User 2 and User 3 to org
    (doseq [email ["u2@test.com" "u3@test.com"]]
      (let [uid (th/user-id-by-email ds email)]
        (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id) VALUES (?, ?)" org-id uid])))

    (let [pelada-id (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                  (mock/json-body {:organization_id org-id :num_teams 2})
                                                  auth1))))]

      ;; Prepare pelada: Teams -> Close Attendance -> Begin -> Close
      (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team A"}) auth1))
      (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team B"}) auth1))

      ;; Add User 1 and User 2 to teams (User 3 is NOT in a team)
      (let [p1-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ?" (th/user-id-by-email ds "u1@test.com")]))))
            p2-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ?" (th/user-id-by-email ds "u2@test.com")]))))
            t1-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from Teams where pelada_id = ?" pelada-id]))))]
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t1-id p1-id])
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t1-id p2-id]))

      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth1))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth1))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close")) auth1))

      ;; 2. Test initial voting info
      (let [info1 (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth1)))]
        (is (true? (:can_vote info1)))
        (is (false? (:has_voted info1)))
        (is (empty? (:current_votes info1))))

      ;; 3. User 1 votes
      (let [info1 (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth1)))
            voter1-id (:voter_player_id info1)
            target-id (:player_id (first (:eligible_players info1)))]
        (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/votes/batch"))
                 (mock/json-body {:voter_id voter1-id
                                  :votes [{:target_id target-id :stars 5}]})
                 auth1))

        ;; 4. Verify User 1 sees their votes
        (let [info1-after (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth1)))]
          (is (true? (:has_voted info1-after)))
          (is (= 1 (count (:current_votes info1-after))))
          (is (= 5 (:stars (first (:current_votes info1-after))))))

        ;; 5. User 2 votes differently
        (let [info2 (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth2)))
              voter2-id (:voter_player_id info2)
              target2-id (:player_id (first (:eligible_players info2)))]
          (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/votes/batch"))
                   (mock/json-body {:voter_id voter2-id
                                    :votes [{:target_id target2-id :stars 3}]})
                   auth2))

          ;; 6. Verify isolation
          (let [info1-final (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth1)))
                info2-final (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth2)))]
            (is (= 5 (:stars (first (:current_votes info1-final)))))
            (is (= 3 (:stars (first (:current_votes info2-final)))))))

        ;; 7. Edge Case: User 3 (not in team) should not be able to vote
        (let [resp3 (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth3))
              info3 (th/decode-body resp3)]
          (is (= 200 (:status resp3))) ;; Handler returns 200 OK with can_vote: false in catch block
          (is (false? (:can_vote info3)))
          (is (empty? (:eligible_players info3)))
          (is (= "Only players who participated can vote" (:message info3))))))))
