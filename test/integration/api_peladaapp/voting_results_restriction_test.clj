(ns api-peladaapp.voting-results-restriction-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest voting-results-restriction-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})

        ;; Setup: Admin, Player 1 (voted), Player 2 (did not vote), Player 3 (didn't play)
        token-admin (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        auth-admin (th/auth-cookie token-admin)

        token-p1 (th/register-and-login! app {:name "Player 1" :email "p1@test.com" :password "pass"})
        auth-p1 (th/auth-cookie token-p1)

        token-p2 (th/register-and-login! app {:name "Player 2" :email "p2@test.com" :password "pass"})
        auth-p2 (th/auth-cookie token-p2)

        token-p3 (th/register-and-login! app {:name "Player 3" :email "p3@test.com" :password "pass"})
        auth-p3 (th/auth-cookie token-p3)

        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Restriction Org"})
                                             auth-admin))))]

    ;; Add players to org
    (doseq [email ["p1@test.com" "p2@test.com" "p3@test.com"]]
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
          p2-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p2@test.com") org-id)
          _p3-id (th/player-id-by-user-id ds (th/user-id-by-email ds "p3@test.com") org-id)]

      ;; Only Admin, P1 and P2 participated
      (doseq [pid [p-admin-id p1-id p2-id]]
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t1-id pid]))

      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth-admin))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth-admin))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close")) auth-admin))

      ;; Player 1 votes
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/votes/batch"))
               (mock/json-body {:voter_id p1-id
                                :votes [{:target_id p-admin-id :stars 5}]})
               auth-p1))

      ;; Force voting window to close
      (jdbc/execute! ds ["UPDATE Peladas SET closed_at = datetime('now', '-25 hours') WHERE id = ?" pelada-id])

      (testing "Admin can access results even if didn't vote"
        (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-results")) auth-admin))]
          (is (= 200 (:status resp)))))

      (testing "Player 1 (participated and voted) can access results"
        (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-results")) auth-p1))]
          (is (= 200 (:status resp)))))

      (testing "Player 2 (participated and DID NOT vote) CANNOT access results"
        (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-results")) auth-p2))
              body (th/decode-body resp)]
          (is (= 403 (:status resp)))
          (is (= "Você precisa votar para ter acesso aos resultados da pelada." (:message body)))))

      (testing "Player 3 (did NOT participate) can access results"
        (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-results")) auth-p3))]
          (is (= 200 (:status resp))))))))
