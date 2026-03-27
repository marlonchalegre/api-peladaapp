(ns api-peladaapp.voting-disable-test
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest voting-disable-feature-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})

        ;; Setup: 3 users
        token1 (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        auth1 (th/auth-header token1)
        token2 (th/register-and-login! app {:name "Player 1" :email "p1@test.com" :password "pass"})
        auth2 (th/auth-header token2)
        token3 (th/register-and-login! app {:name "Player 2" :email "p2@test.com" :password "pass"})
        auth3 (th/auth-header token3)

        ;; Admin creates org
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Voting Test Org"})
                                             auth1))))]

    ;; Add P1 and P2 to org
    (doseq [email ["p1@test.com" "p2@test.com"]]
      (let [uid (th/user-id-by-email ds email)]
        (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id) VALUES (?, ?)" org-id uid])))

    (let [pelada-id (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                  (mock/json-body {:organization_id org-id :num_teams 2})
                                                  auth1))))]

      ;; Create teams
      (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team A"}) auth1))
      (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team B"}) auth1))

      ;; Get player IDs and team IDs
      (let [admin-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ?" (th/user-id-by-email ds "admin@test.com")]))))
            p1-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ?" (th/user-id-by-email ds "p1@test.com")]))))
            p2-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ?" (th/user-id-by-email ds "p2@test.com")]))))
            t1-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from Teams where pelada_id = ?" pelada-id]))))
            t2-id (:id (misc/unamespace (second (jdbc/execute! ds ["select id from Teams where pelada_id = ?" pelada-id]))))]

        ;; Add players to teams
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t1-id admin-id])
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t1-id p1-id])
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t2-id p2-id])

        ;; Create attendance records for all players with voting_enabled = 1
        (doseq [pid [admin-id p1-id p2-id]]
          (jdbc/execute! ds ["INSERT INTO peladaattendance (pelada_id, player_id, status, voting_enabled) VALUES (?, ?, 'confirmed', 1)"
                             pelada-id pid]))

        ;; Transition pelada through states
        (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth1))
        (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth1))
        (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close")) auth1))

        (testing "Initial state: all players enabled for voting"
          (let [info (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth1)))
                eligible-players (:eligible_players info)]
            (is (>= (count eligible-players) 2) (str "Expected at least 2 eligible players, got " (count eligible-players)))
            (is (every? #(true? (:voting_enabled %)) eligible-players) "All eligible players should have voting_enabled = true")))

        (testing "Admin disables P2 from voting"
          (let [resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance/voting-enabled"))
                              (mock/json-body {:player_id p2-id :enabled false})
                              auth1))
                status (:status resp)]
            (is (= 200 status) (str "Expected status 200, got " status))

            (let [info (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth1)))
                  p2-voting (first (filter #(= p2-id (:player_id %)) (:eligible_players info)))]
              (is (not (:voting_enabled p2-voting)) "P2 should have voting_enabled = false"))))

        (testing "Casting votes for disabled player should fail"
          (let [resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/votes/batch"))
                              (mock/json-body {:voter_id p1-id
                                               :votes [{:target_id p2-id :stars 5}]})
                              auth2))
                status (:status resp)
                body (th/decode-body resp)]
            (is (= 400 status) (str "Expected status 400, got " status " with body: " body))
            (is (= "Cannot vote for a player who has voting disabled for this pelada" (:message body))
                (str "Unexpected error message: " (:message body)))))))))
