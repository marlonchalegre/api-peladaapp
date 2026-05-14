(ns api-peladaapp.fixed-goalkeepers-test
  (:require
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest global-fixed-goalkeepers-flow-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]

    ;; Register and login
    (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass123"})
    (let [token (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass123"})
          auth (fn [req] (mock/cookie req "authToken" token))

          ;; Create organization
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {"name" "Global GK Club"})
                            auth))
          org-id (misc/as-uuid (:id (th/decode-body org-resp)))
          user-id (th/user-id-by-email ds "admin@test.com")
          player-id (th/player-id-by-user-id ds user-id org-id)

          ;; Add a second player so teams can be formed
          _ (th/register-and-login! app {:name "Player 2" :email "p2@test.com" :password "pass123"})
          p2-user-id (th/user-id-by-email ds "p2@test.com")
          p2-player-id (db.player/insert-player {:user-id p2-user-id :organization-id org-id :grade 5.0 :member-type "mensalista"} ds)]

      (is (= 201 (:status org-resp)))

      (testing "create pelada with fixed goalkeepers"
        (let [resp (app (-> (mock/request :post "/api/peladas")
                            (auth)
                            (mock/json-body {"organization_id" org-id
                                             "num_teams" 2
                                             "players_per_team" 5
                                             "fixed_goalkeepers" true})))
              body (th/decode-body resp)
              pelada-id (misc/as-uuid (:id body))]
          (is (= 201 (:status resp)))
          (is (= true (:fixed_goalkeepers body)))

          (testing "setting and reassigning goalkeepers"
            (let [set-resp (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                                    (auth)
                                    (mock/json-body {"home_fixed_goalkeeper_id" player-id})))]
              (is (= 200 (:status set-resp)))
              (is (= (str player-id) (str (:home_fixed_goalkeeper_id (th/decode-body set-resp))))))

            ;; Swap to away
            (let [swap-resp (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                                     (auth)
                                     (mock/json-body {"home_fixed_goalkeeper_id" nil
                                                      "away_fixed_goalkeeper_id" player-id})))]
              (is (= 200 (:status swap-resp)))
              (let [b (th/decode-body swap-resp)]
                (is (nil? (:home_fixed_goalkeeper_id b)))
                (is (= (str player-id) (str (:away_fixed_goalkeeper_id b)))))))

          (testing "randomization ignores global fixed goalkeeper"
            ;; Put it back to home for test
            (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                     (auth)
                     (mock/json-body {"home_fixed_goalkeeper_id" player-id "away_fixed_goalkeeper_id" nil})))

            ;; Confirm attendance and close list
            (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
                     (mock/json-body {"status" "confirmed"})
                     auth))
            (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
                     (mock/json-body {"status" "confirmed" "player_id" p2-player-id})
                     auth))
            (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance"))
                     auth))

            (let [rand-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/teams/randomize"))
                                     (auth)
                                     (mock/json-body {"player_ids" [player-id p2-player-id] "players_per_team" 5})))]
              (is (= 200 (:status rand-resp)))

              ;; Verify player is NOT in any team (player-id is fixed GK, so should be excluded from teams)
              (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) (auth))))]
                (is (not (some #(= (str player-id) (str (:id %))) (mapcat :players (:teams details))))))))

          (testing "match lineups injection"
            (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                     (mock/json-body {"matches_per_team" 1})
                     auth))

            (let [dashboard (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) (auth))))
                  match (first (:matches dashboard))
                  mid (keyword (str (:id match)))
                  lineups (get (:match_lineups_map dashboard) mid)
                  all-lineup-players (mapcat val lineups)]
              (is (some (fn [p] (and (= (str player-id) (str (:player_id p))) (= true (:is_goalkeeper p)))) all-lineup-players))))))

      (testing "behavior when feature is disabled"
        (let [resp (app (-> (mock/request :post "/api/peladas")
                            (auth)
                            (mock/json-body {"organization_id" org-id
                                             "num_teams" 2
                                             "players_per_team" 5
                                             "fixed_goalkeepers" false})))
              pelada-id (misc/as-uuid (:id (th/decode-body resp)))]

          ;; Randomization should NOT ignore the player if they are passed in player_ids
          (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth))
          (let [rand-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/teams/randomize"))
                                   (auth)
                                   (mock/json-body {"player_ids" [player-id] "players_per_team" 5})))]
            (is (= 200 (:status rand-resp)))
            (let [details (th/decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details")) (auth))))]
              (is (some #(= (str player-id) (str (:id %))) (mapcat :players (:teams details)))))))))))
