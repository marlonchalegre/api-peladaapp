(ns api-peladaapp.voting-results-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest voting-results-and-status-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})

        ;; Setup: Admin, Player 1, Player 2
        token-admin (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        auth-admin (th/auth-cookie token-admin)
        token-p1 (th/register-and-login! app {:name "Player 1" :email "p1@test.com" :password "pass"})
        auth-p1 (th/auth-cookie token-p1)
        _ (th/register-and-login! app {:name "Player 2" :email "p2@test.com" :password "pass"})

        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Voting Results Org"})
                                             auth-admin))))]

    ;; Add players to org
    (doseq [email ["p1@test.com" "p2@test.com"]]
      (let [uid (th/user-id-by-email ds email)]
        (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id) VALUES (?, ?)" org-id uid])))

    (let [pelada-id (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                  (mock/json-body {:organization_id org-id :num_teams 2})
                                                  auth-admin))))
          t1-id (:id (th/decode-body (app (-> (mock/request :post "/api/teams")
                                              (mock/json-body {:pelada_id pelada-id :name "Team A"})
                                              auth-admin))))
          p-admin-id (th/player-id-by-user-id ds (th/user-id-by-email ds "admin@test.com") org-id)
          p1-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p1@test.com") org-id)
          p2-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p2@test.com") org-id)]
      (doseq [pid [p-admin-id p1-id p2-id]]
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t1-id pid]))

      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth-admin))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth-admin))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close")) auth-admin))

      (testing "Accessing results while voting is open should fail"
        (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-results")) auth-admin))
              body (th/decode-body resp)]
          (is (= 400 (:status resp)))
          (is (= "Results are only available after the voting period ends (24h after close)." (:message body)))))

      (testing "Accessing status while voting is open should succeed"
        (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-status")) auth-admin))
              body (th/decode-body resp)]
          (is (= 200 (:status resp)))
          (is (contains? body :voters))
          (is (= 3 (:total_eligible body)))
          (is (= 0 (:total_voted body)))))

      (testing "Voting updates status"
        ;; Player 1 votes for Admin and Player 2
        (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/votes/batch"))
                 (mock/json-body {:voter_id p1-id
                                  :votes [{:target_id p-admin-id :stars 5}
                                          {:target_id p2-id :stars 4}]})
                 auth-p1))

        (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-status")) auth-admin))
              body (th/decode-body resp)]
          (is (= 1 (:total_voted body)))
          (let [p1-status (first (filter #(= p1-id (:player_id %)) (:voters body)))]
            (is (true? (:has_voted p1-status))))))

      (testing "Accessing results after voting window closes should succeed"
        ;; Force close-at to be more than 24h ago
        (jdbc/execute! ds ["UPDATE Peladas SET closed_at = datetime('now', '-25 hours') WHERE id = ?" pelada-id])

        (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-results")) auth-admin))
              body (th/decode-body resp)]
          (is (= 200 (:status resp)))
          (is (contains? body :mvp))
          (is (contains? body :voters))
          (is (= 1 (:total_voted body)))

          ;; Check MVP results (only Admin and Player 2 received votes)
          (let [mvp-admin (first (filter #(= p-admin-id (:player_id %)) (:mvp body)))
                mvp-p2 (first (filter #(= p2-id (:player_id %)) (:mvp body)))]
            (is (= 5.0 (:average_stars mvp-admin)))
            (is (= 4.0 (:average_stars mvp-p2)))))))))
