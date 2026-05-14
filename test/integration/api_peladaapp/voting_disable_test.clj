(ns api-peladaapp.voting-disable-test
  (:require
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- exec-one! [ds query]
  (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest voting-disable-feature-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)

        ;; Setup: 3 users
        token1 (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        auth1 (th/auth-cookie token1)
        token2 (th/register-and-login! app {:name "Player 1" :email "p1@test.com" :password "pass"})
        auth2 (th/auth-cookie token2)
        _ (th/register-and-login! app {:name "Player 2" :email "p2@test.com" :password "pass"})

        ;; Admin creates org
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Voting Test Org"})
                                             auth1))))]

    ;; Add P1 and P2 to org
    (doseq [email ["p1@test.com" "p2@test.com"]]
      (let [uid (th/user-id-by-email ds email)]
        (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id (misc/as-uuid org-id) :user_id (misc/as-uuid uid) :member_type [:cast "diarista" :member_type]}])))))

    (let [pelada-id (:id (th/decode-body (app (-> (mock/request :post "/api/peladas")
                                                  (mock/json-body {:organization_id org-id :num_teams 2})
                                                  auth1))))]

      ;; Create teams
      (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team A"}) auth1))
      (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id pelada-id :name "Team B"}) auth1))

      ;; Get player IDs and team IDs
      (let [admin-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id (misc/as-uuid (th/user-id-by-email ds "admin@test.com"))]))))
            p1-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id (misc/as-uuid (th/user-id-by-email ds "p1@test.com"))]))))
            p2-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id (misc/as-uuid (th/user-id-by-email ds "p2@test.com"))]))))
            teams (exec! ds (-> (h/select :id) (h/from :Teams) (h/where [:= :pelada_id (misc/as-uuid pelada-id)]) (h/order-by :id)))
            t1-id (:id (first teams))
            t2-id (:id (second teams))]

        ;; Add players to teams
        (exec! ds (-> (h/insert-into :TeamPlayers) (h/values [{:team_id (misc/as-uuid t1-id) :player_id (misc/as-uuid admin-id)}
                                                              {:team_id (misc/as-uuid t1-id) :player_id (misc/as-uuid p1-id)}
                                                              {:team_id (misc/as-uuid t2-id) :player_id (misc/as-uuid p2-id)}])))

        ;; Create attendance records for all players with voting_enabled = true
        (doseq [pid [admin-id p1-id p2-id]]
          (exec! ds (-> (h/insert-into :Attendance)
                        (h/values [{:pelada_id (misc/as-uuid pelada-id) :player_id (misc/as-uuid pid) :status [:cast "confirmed" :attendance_status] :voting_enabled true}]))))

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
                  p2-voting (first (filter #(= (str p2-id) (str (:player_id %))) (:eligible_players info)))]
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
