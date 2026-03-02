(ns api-peladaapp.voting-stats-reproduction-test
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest voting-info-stats-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})

        token1 (th/register-and-login! app {:name "User 1" :email "u1@test.com" :password "pass"})
        auth1 (th/auth-header token1)
        token2 (th/register-and-login! app {:name "User 2" :email "u2@test.com" :password "pass"})
        auth2 (th/auth-header token2)

        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Stats Org"})
                                             auth1))))]

    (doseq [email ["u2@test.com"]]
      (let [uid (th/user-id-by-email ds email)]
        (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id) VALUES (?, ?)" org-id uid])))

    (let [pelada-id (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                  (mock/json-body {:organization_id org-id :num_teams 2})
                                                  auth1))))]

      (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team A"}) auth1))
      (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team B"}) auth1))

      (let [p1-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ?" (th/user-id-by-email ds "u1@test.com")]))))
            p2-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ?" (th/user-id-by-email ds "u2@test.com")]))))
            t1-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from Teams where pelada_id = ?" pelada-id]))))
            t2-id (:id (misc/unamespace (second (jdbc/execute! ds ["select id from Teams where pelada_id = ?" pelada-id]))))]
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t1-id p1-id])
        (jdbc/execute! ds ["INSERT INTO TeamPlayers (team_id, player_id) VALUES (?, ?)" t2-id p2-id]))

      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth1))
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth1))

      ;; Record some events for User 2
      (let [match-id (:id (first (:matches (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth1))))))
            p2-id (:id (misc/unamespace (first (jdbc/execute! ds ["select id from OrganizationPlayers where user_id = ?" (th/user-id-by-email ds "u2@test.com")]))))]
        (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                 (mock/json-body {:player_id p2-id :event_type "goal"})
                 auth1))
        (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                 (mock/json-body {:player_id p2-id :event_type "assist"})
                 auth1)))

      ;; DEBUG: Check DB state
      (println "MatchEvents:" (jdbc/execute! ds ["select * from MatchEvents"]))
      (println "PeladaPlayerStats:" (jdbc/execute! ds ["select * from PeladaPlayerStats"]))

      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close")) auth1))

      ;; Test voting info contains stats
      (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/voting-info")) auth1))
            info (th/decode-body resp)]
        (println "DECODED INFO:" info)
        (is (= 200 (:status resp)))
        (is (true? (:can_vote info)))
        (let [eligible (:eligible_players info)
              p2-info (first (filter #(= "User 2" (:name %)) eligible))]
          (is (some? p2-info) (str "User 2 should be eligible. Eligible: " eligible))
          (is (= 1 (:goals p2-info)) "Should have 1 goal")
          (is (= 1 (:assists p2-info)) "Should have 1 assist")
          (is (= 0 (:own_goals p2-info)) "Should have 0 own goals"))))))