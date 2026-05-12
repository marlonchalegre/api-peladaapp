(ns api-peladaapp.match-edit-test
  (:require
   [api-peladaapp.server :as server]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest match-edit-test
  (let [db-raw (-> th/*test-system* :database :database)
        db (if (fn? db-raw) (db-raw) db-raw)
        app (fn [req] (server/app (assoc req :database db)))

        token (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        auth (th/auth-cookie token)

        ;; Setup: Org -> Pelada -> Teams -> Begin
        org-resp (app (-> (mock/request :post "/api/organizations") (mock/json-body {:name "Org"}) auth))
        org-id (:id (th/decode-body org-resp))
        pelada-resp (app (-> (mock/request :post "/api/peladas") (mock/json-body {:organization_id org-id}) auth))
        pelada-id (:id (th/decode-body pelada-resp))

        ;; Add a player (the admin itself is already in the org)
        _ (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team A"}) auth))
        _ (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team B"}) auth))
        _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth))
        _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth))

        ;; Get matches
        dashboard-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth))
        dashboard (th/decode-body dashboard-resp)
        match-id (-> dashboard :Matches first :id)
        player-id (-> dashboard :organization_players first :id)]

    (testing "Record event in running match"
      (let [resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                          (mock/json-body {:player_id player-id :event_type "goal"})
                          auth))]
        (is (= 200 (:status resp)))))

    (testing "Finish match"
      (let [resp (app (-> (mock/request :put (str "/api/matches/" match-id "/score"))
                          (mock/json-body {:home_score 1 :away_score 0 :status "finished"})
                          auth))]
        (is (= 200 (:status resp)))))

    (testing "Edit finished match while pelada is running"
      (let [resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                          (mock/json-body {:player_id player-id :event_type "goal"})
                          auth))]
        (is (= 200 (:status resp)) "Should allow recording events in finished matches if pelada is running")))

    (testing "Close pelada"
      (let [resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close")) auth))]
        (is (= 200 (:status resp)))))

    (testing "Editing match after pelada is closed should now succeed for admin"
      (let [resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                          (mock/json-body {:player_id player-id :event_type "goal"})
                          auth))]
        (is (= 200 (:status resp)) "Should allow recording events after pelada is closed if admin")))

    (testing "Editing match after pelada is closed should FAIL for non-admin"
      (let [token2 (th/register-and-login! app {:name "Player" :email "player@test.com" :password "pass"})
            auth2 (th/auth-cookie token2)
            resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                          (mock/json-body {:player_id player-id :event_type "goal"})
                          auth2))]
        (is (= 403 (:status resp)) "Non-admin should be forbidden")))))
