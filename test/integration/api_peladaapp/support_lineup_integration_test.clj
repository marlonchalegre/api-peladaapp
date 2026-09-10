(ns api-peladaapp.support-lineup-integration-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest support-lineup-endpoints-integration-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]

    ;; Register admin & regular user
    (app (-> (mock/request :post "/auth/register")
             (mock/json-body {"name" "Admin" "email" "admin@test.com" "password" "pass123"})))
    (app (-> (mock/request :post "/auth/register")
             (mock/json-body {"name" "Regular" "email" "regular@test.com" "password" "pass123"})))

    (let [admin-login (app (-> (mock/request :post "/auth/login")
                               (mock/json-body {"email" "admin@test.com" "password" "pass123"})))
          regular-login (app (-> (mock/request :post "/auth/login")
                                 (mock/json-body {"email" "regular@test.com" "password" "pass123"})))
          admin-token (:token (th/decode-body admin-login))
          regular-token (:token (th/decode-body regular-login))
          admin-auth (th/auth-cookie admin-token)
          regular-auth (th/auth-cookie regular-token)]

      (th/grant-org-creation! ds "admin@test.com")

      (let [org-resp (app (-> (mock/request :post "/api/organizations")
                              (mock/json-body {"name" "Support Lineup Club"})
                              admin-auth))
            org-id (:id (th/decode-body org-resp))

            pelada-resp (app (-> (mock/request :post "/api/peladas")
                                 (admin-auth)
                                 (mock/json-body {"organization_id" org-id
                                                  "num_teams" 3
                                                  "players_per_team" 2})))
            pelada-id (:id (th/decode-body pelada-resp))]

        (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance"))
                 admin-auth))

        (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                                  admin-auth
                                  (mock/json-body {"matches_per_team" 2})))
              dashboard (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data"))
                                                 admin-auth)))
              matches (:matches dashboard)
              first-match (first matches)
              first-match-id (:id first-match)]

          (is (= 200 (:status begin-resp)))
          (is (seq matches))

          (testing "POST /api/peladas/:id/support-lineup/generate as admin succeeds"
            (let [resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/support-lineup/generate"))
                                admin-auth))]
              (is (= 200 (:status resp)))
              (let [body (th/decode-body resp)]
                (is (sequential? body))
                (is (= (count matches) (count body))))))

          (testing "POST /api/peladas/:id/support-lineup/generate as non-admin returns 403"
            (let [resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/support-lineup/generate"))
                                regular-auth))]
              (is (= 403 (:status resp)))))

          (testing "PUT /api/matches/:id/support-lineup updates support pair"
            (let [players-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/players"))
                                        admin-auth))
                  players (th/decode-body players-resp)
                  valid-player-id (:id (first players))
                  resp (app (-> (mock/request :put (str "/api/matches/" first-match-id "/support-lineup"))
                                admin-auth
                                (mock/json-body {"support_camera_player_id" (str valid-player-id)
                                                 "support_stats_player_id" nil})))]
              (is (= 200 (:status resp)))
              (let [body (th/decode-body resp)]
                (is (= (str valid-player-id) (:support_camera_player_id body)))
                (is (nil? (:support_stats_player_id body))))))

          (testing "PUT /api/matches/:id/support-lineup as non-admin returns 403"
            (let [resp (app (-> (mock/request :put (str "/api/matches/" first-match-id "/support-lineup"))
                                regular-auth
                                (mock/json-body {"support_camera_player_id" nil})))]
              (is (= 403 (:status resp)))))

          (testing "POST /api/matches/:id/support-lineup/reroll re-rolls match support"
            (let [resp (app (-> (mock/request :post (str "/api/matches/" first-match-id "/support-lineup/reroll"))
                                admin-auth))]
              (is (= 200 (:status resp)))
              (let [body (th/decode-body resp)]
                (is (= first-match-id (:id body))))))

          (testing "POST /api/matches/:id/support-lineup/reroll with non-existent match returns 404"
            (let [non-existent-id (java.util.UUID/randomUUID)
                  resp (app (-> (mock/request :post (str "/api/matches/" non-existent-id "/support-lineup/reroll"))
                                admin-auth))]
              (is (= 404 (:status resp))))))))))
