(ns integration.api-peladaapp.statistics-test
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [medley.core :as medley.core]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- exec-one! [ds query]
  (let [result (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps})]
    (medley.core/assoc-some result :id (or (:id result) (get result "id") (first (vals result))))))

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest organization-statistics-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        _ (th/register-and-login! app {:name "User 1" :email "user_1_3882@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "user_1_3882@test.com")

        org-resp (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org Test 9337 8072 5211 2091 9111 9041 7462 5454 3094"}]) (h/returning :id)))
        org-id (:id org-resp)
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id user-id :grade 5.0}])))
        player-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id user-id] [:= :organization_id org-id]))))

        pelada-resp (exec-one! ds (-> (h/insert-into :Peladas) (h/values [{:organization_id org-id :scheduled_at [[:cast "2026-01-10 10:00:00" :timestamp]] :status "closed"}]) (h/returning :id)))
        pelada-id (:id pelada-resp)
        team-a-resp (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Team A"}]) (h/returning :id)))
        team-a-id (:id team-a-resp)
        team-b-resp (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Team B"}]) (h/returning :id)))
        team-b-id (:id team-b-resp)
        match-resp (exec-one! ds (-> (h/insert-into :Matches) (h/values [{:pelada_id pelada-id :home_team_id team-a-id :away_team_id team-b-id :sequence 1 :status "finished"}]) (h/returning :id)))
        match-id (:id match-resp)]

    (exec! ds (-> (h/insert-into :matchlineups) (h/values [{:match_id match-id :team_id team-a-id :player_id player-id}])))
    (exec! ds (-> (h/insert-into :MatchEvents) (h/values [{:match_id match-id :player_id player-id :event_type "goal"}
                                                          {:match_id match-id :player_id player-id :event_type "goal"}
                                                          {:match_id match-id :player_id player-id :event_type "assist"}]))))

  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (:id (exec-one! ds (-> (h/select :id) (h/from :Organizations) (h/where [:= :name "Org Test 9337 8072 5211 2091 9111 9041 7462 5454 3094"]))))
        token (th/register-and-login! app {:name "User 1" :email "user_1_3882@test.com" :password "pass123"})
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                          (mock/query-string {:year 2026})
                          ((th/auth-cookie token))))
        body (th/decode-body response)
        stat (first body)]
    (is (= 200 (:status response)))
    (is (= 1 (:peladas_played stat)))
    (is (= 2 (:goal stat)))
    (is (= 1 (:assist stat)))
    (is (= 0 (:own_goal stat)))
    (is (= 0.0 (:avg_rating stat)))))

(deftest organization-statistics-with-rating-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "User Rating" :email "rating_7781@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "rating_7781@test.com")

        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id user-id :grade 5.0}])))
        player-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id user-id] [:= :organization_id org-id]))))

        pelada-id (:id (exec-one! ds (-> (h/insert-into :Peladas) (h/values [{:organization_id org-id :scheduled_at [[:cast "2026-03-13 10:00:00" :timestamp]] :status "closed"}]) (h/returning :id))))
        team-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Team Rating"}]) (h/returning :id))))
        opp-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Opponent"}]) (h/returning :id))))
        match-id (:id (exec-one! ds (-> (h/insert-into :Matches) (h/values [{:pelada_id pelada-id :home_team_id team-id :away_team_id opp-id :sequence 1 :status "finished"}]) (h/returning :id))))]

    (exec! ds (-> (h/insert-into :matchlineups) (h/values [{:match_id match-id :team_id team-id :player_id player-id}])))

    ;; Add Votes
    (exec! ds (-> (h/insert-into :Votes) (h/values [{:pelada_id pelada-id :voter_id player-id :target_id player-id :stars 5}])))

    (let [_ (th/register-and-login! app {:name "User 2" :email "user_2_4420@test.com" :password "pass123"})
          user2-id (th/user-id-by-email ds "user_2_4420@test.com")
          _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id user2-id :grade 5.0}])))
          player2-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id user2-id] [:= :organization_id org-id]))))]
      (exec! ds (-> (h/insert-into :Votes) (h/values [{:pelada_id pelada-id :voter_id player2-id :target_id player-id :stars 4}]))))

    (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                            (mock/query-string {:year 2026})
                            ((th/auth-cookie token))))
          body (th/decode-body response)
          stat (first (filter #(= "User Rating" (:player_name %)) body))]
      (is (= 200 (:status response)))
      (is (= 4.5 (:avg_rating stat))))))

(deftest zero-stats-player-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Zero Stats User" :email "zero_3591@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "zero_3591@test.com")

        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id user-id :grade 5.0}])))
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                          (mock/query-string {:year 2026})
                          ((th/auth-cookie token))))
        body (th/decode-body response)]
    (is (= 200 (:status response)))
    (is (empty? body) "Players with no participation should not be in statistics")))

(deftest empty-year-statistics-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "User" :email "user_7036@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "user_7036@test.com")

        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id user-id :grade 5.0}])))
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                          (mock/query-string {:year 2026})
                          ((th/auth-cookie token))))
        body (th/decode-body response)]
    (is (= 200 (:status response)))
    (is (empty? body))))

(deftest statistics-all-years-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Legacy User" :email "legacy_5722@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "legacy_5722@test.com")

        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id user-id :grade 5.0}])))
        player-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id user-id] [:= :organization_id org-id]))))

        ;; Old pelada from 2025
        pelada-id (:id (exec-one! ds (-> (h/insert-into :Peladas) (h/values [{:organization_id org-id :scheduled_at [[:cast "2025-05-15 10:00:00" :timestamp]] :status "closed"}]) (h/returning :id))))
        team-a-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Legacy Team A"}]) (h/returning :id))))
        _ (exec-one! ds (-> (h/insert-into :Matches) (h/values [{:pelada_id pelada-id :home_team_id team-a-id :away_team_id team-a-id :sequence 1 :status "finished"}]) (h/returning :id)))]

    ;; Add Player to TeamPlayers (Simulating legacy data structure)
    (exec! ds (-> (h/insert-into :TeamPlayers) (h/values [{:team_id team-a-id :player_id player-id}])))

    (testing "Query without year should return data from all years"
      (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                              ((th/auth-cookie token))))
            body (th/decode-body response)
            stat (first body)]
        (is (= 200 (:status response)))
        (is (= 1 (:peladas_played stat)))))))

(deftest statistics-participation-logic-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "User" :email "user_6337@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "user_6337@test.com")

        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id user-id :grade 5.0}])))
        player-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id user-id] [:= :organization_id org-id]))))

        pelada-id (:id (exec-one! ds (-> (h/insert-into :Peladas) (h/values [{:organization_id org-id :scheduled_at [[:cast "2026-06-01 10:00:00" :timestamp]] :status "closed"}]) (h/returning :id))))
        team-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Team A"}]) (h/returning :id))))
        opponent-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Team B"}]) (h/returning :id))))
        match-id (:id (exec-one! ds (-> (h/insert-into :Matches) (h/values [{:pelada_id pelada-id :home_team_id team-id :away_team_id opponent-id :sequence 1 :status "finished"}]) (h/returning :id))))]

    ;; Player participated but NO events
    (exec! ds (-> (h/insert-into :matchlineups) (h/values [{:match_id match-id :team_id team-id :player_id player-id}])))

    (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                            (mock/query-string {:year 2026})
                            ((th/auth-cookie token))))
          body (th/decode-body response)
          stat (first (filter #(= (str player-id) (str (:player_id %))) body))]
      (is (= 200 (:status response)))
      (is (= 1 (:peladas_played stat)) "Should count peladas via participation even without events"))))

(deftest statistics-unauthorized-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org"}]) (h/returning :id))))

        response (app (mock/request :get (str "/api/organizations/" org-id "/statistics")))]
      ;; Should return 401 Unauthorized because no token is provided
    (is (= 401 (:status response)))))
