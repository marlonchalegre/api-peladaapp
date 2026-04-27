(ns integration.api-peladaapp.manual-stats-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- setup-org-with-admin-and-player [app ds suffix]
  (let [admin-email (str "admin" suffix "@test.com")
        player-email (str "player" suffix "@test.com")
        admin-token (th/register-and-login! app {:name "Admin" :email admin-email :password "pass123"})
        admin-id (th/user-id-by-email ds admin-email)
        player-token (th/register-and-login! app {:name "Player" :email player-email :password "pass123"})
        player-id (th/user-id-by-email ds player-email)
        _ (jdbc/execute! ds ["INSERT INTO Organizations (name) VALUES (?)" (str "Org Test " suffix)])
        org-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO OrganizationAdmins (organization_id, user_id) VALUES (?, ?)" org-id admin-id])
        _ (jdbc/execute! ds ["INSERT INTO OrganizationPlayers (organization_id, user_id, grade) VALUES (?, ?, 5.0)" org-id player-id])
        org-player-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)]
    {:admin-token admin-token
     :player-token player-token
     :org-id org-id
     :org-player-id org-player-id}))

(deftest upsert-manual-stats-as-admin-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        {:keys [admin-token org-id org-player-id]} (setup-org-with-admin-and-player app ds "admin")
        stats [{:player-id org-player-id :year 2026 :goals 10 :assists 5 :own-goals 1}]
        response (app (-> (mock/request :post (str "/api/organizations/" org-id "/manual-stats"))
                          (mock/json-body stats)
                          ((th/auth-cookie admin-token))))]
    (is (= 200 (:status response)))
    (is (= 1 (-> response th/decode-body :updated)))))

(deftest upsert-manual-stats-as-non-admin-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        {:keys [player-token org-id org-player-id]} (setup-org-with-admin-and-player app ds "nonadmin")
        stats [{:player-id org-player-id :year 2026 :goals 10 :assists 5 :own-goals 1}]
        response (app (-> (mock/request :post (str "/api/organizations/" org-id "/manual-stats"))
                          (mock/json-body stats)
                          ((th/auth-cookie player-token))))]
    (is (= 403 (:status response)))))

(deftest statistics-includes-manual-stats-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        {:keys [admin-token player-token org-id org-player-id]} (setup-org-with-admin-and-player app ds "stats")

        ;; 1. Add Manual Stats: 10 goals, 5 assists
        _ (app (-> (mock/request :post (str "/api/organizations/" org-id "/manual-stats"))
                   (mock/json-body [{:player-id org-player-id :year 2026 :goals 10 :assists 5 :own-goals 1}])
                   ((th/auth-cookie admin-token))))

        ;; 2. Add Match and Event: 2 goals from matches
        _ (jdbc/execute! ds ["INSERT INTO Peladas (organization_id, scheduled_at, status) VALUES (?, '2026-01-01 10:00:00', 'closed')" org-id])
        pelada-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (?, 'Team A')" pelada-id])
        team-a-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Teams (pelada_id, name) VALUES (?, 'Team B')" pelada-id])
        team-b-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO Matches (pelada_id, home_team_id, away_team_id, sequence, status) VALUES (?, ?, ?, 1, 'finished')" pelada-id team-a-id team-b-id])
        match-id (-> (jdbc/execute-one! ds ["SELECT last_insert_rowid() as id"]) :id)
        _ (jdbc/execute! ds ["INSERT INTO MatchLineups (match_id, player_id, team_id) VALUES (?, ?, 1)" match-id org-player-id])
        _ (jdbc/execute! ds ["INSERT INTO MatchEvents (match_id, player_id, event_type) VALUES (?, ?, 'goal')" match-id org-player-id])
        _ (jdbc/execute! ds ["INSERT INTO MatchEvents (match_id, player_id, event_type) VALUES (?, ?, 'goal')" match-id org-player-id])

        ;; Fetch statistics (total should be 10 + 2 = 12 goals)
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                          (mock/query-string {:year 2026})
                          ((th/auth-cookie player-token))))
        body (th/decode-body response)
        stat (first body)]

    (is (= 200 (:status response)))
    (is (= "Player" (:player_name stat)))
    (is (= 12 (:goal stat))) ;; 10 manual + 2 match
    (is (= 5 (:assist stat)))
    (is (= 1 (:own_goal stat)))
    (is (= 1 (:peladas_played stat)))))

(deftest manual-stats-additive-aggregation-test
  (let [app (-> th/*test-system* :app :handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        {:keys [admin-token player-token org-id org-player-id]} (setup-org-with-admin-and-player app ds "additive")

        ;; 1. First adjustment: 5 goals
        _ (app (-> (mock/request :post (str "/api/organizations/" org-id "/manual-stats"))
                   (mock/json-body [{:player-id org-player-id :year 2026 :goals 5}])
                   ((th/auth-cookie admin-token))))

        ;; 2. Second adjustment tomorrow: 3 goals
        _ (app (-> (mock/request :post (str "/api/organizations/" org-id "/manual-stats"))
                   (mock/json-body [{:player-id org-player-id :year 2026 :goals 3}])
                   ((th/auth-cookie admin-token))))

        ;; Fetch statistics (total should be 5 + 3 = 8 goals)
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                          (mock/query-string {:year 2026})
                          ((th/auth-cookie player-token))))
        body (th/decode-body response)
        stat (first body)]

    (is (= 200 (:status response)))
    (is (= 8 (:goal stat)))))
