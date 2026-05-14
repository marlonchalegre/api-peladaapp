(ns integration.api-peladaapp.manual-stats-test
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- exec-one! [ds query]
  (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- setup-org-with-admin-and-player [app ds suffix]
  (let [admin-email (str "admin" suffix "@test.com")
        player-email (str "player" suffix "@test.com")
        admin-token (th/register-and-login! app {:name "Admin" :email admin-email :password "pass123"})
        admin-id (th/user-id-by-email ds admin-email)
        player-token (th/register-and-login! app {:name "Player" :email player-email :password "pass123"})
        player-id (th/user-id-by-email ds player-email)

        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name (str "Org Test " suffix)}]) (h/returning :id))))
        _ (exec-one! ds (-> (h/insert-into :OrganizationAdmins) (h/values [{:organization_id org-id :user_id admin-id}])))
        org-player-id (:id (exec-one! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id player-id :grade 5.0 :member_type [:cast "diarista" :member_type]}]) (h/returning :id))))]
    {:admin-token admin-token
     :player-token player-token
     :org-id org-id
     :org-player-id org-player-id}))

(deftest upsert-manual-stats-as-admin-test
  (let [app (-> th/*test-system* :app :app-handler)
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
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        {:keys [player-token org-id org-player-id]} (setup-org-with-admin-and-player app ds "nonadmin")
        stats [{:player-id org-player-id :year 2026 :goals 10 :assists 5 :own-goals 1}]
        response (app (-> (mock/request :post (str "/api/organizations/" org-id "/manual-stats"))
                          (mock/json-body stats)
                          ((th/auth-cookie player-token))))]
    (is (= 403 (:status response)))))

(deftest statistics-includes-manual-stats-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        {:keys [admin-token org-id org-player-id]} (setup-org-with-admin-and-player app ds "stats")

        ;; 1. Add Manual Stats: 10 goals, 5 assists
        _ (app (-> (mock/request :post (str "/api/organizations/" org-id "/manual-stats"))
                   (mock/json-body [{:player-id org-player-id :year 2026 :goals 10 :assists 5 :own_goals 1}])
                   ((th/auth-cookie admin-token))))

        ;; 2. Add Match and Event: 2 goals from matches
        pelada-id (:id (exec-one! ds (-> (h/insert-into :Peladas) (h/values [{:organization_id org-id :scheduled_at [[:cast "2026-01-01 10:00:00" :timestamp]] :status [:cast "closed" :pelada_status]}]) (h/returning :id))))
        team-a-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Team A"}]) (h/returning :id))))
        team-b-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Team B"}]) (h/returning :id))))
        match-id (:id (exec-one! ds (-> (h/insert-into :Matches) (h/values [{:pelada_id pelada-id :home_team_id team-a-id :away_team_id team-b-id :sequence 1 :status [:cast "finished" :match_status]}]) (h/returning :id))))]

    (exec-one! ds (-> (h/insert-into :matchlineups) (h/values [{:match_id match-id :player_id org-player-id :team_id team-a-id}])))
    (exec-one! ds (-> (h/insert-into :MatchEvents) (h/values [{:match_id match-id :player_id org-player-id :event_type [:cast "goal" :match_event_type]}
                                                              {:match_id match-id :player_id org-player-id :event_type [:cast "goal" :match_event_type]}]))))

  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (:id (exec-one! ds (-> (h/select :id) (h/from :Organizations) (h/where [:= :name "Org Test stats"]))))
        player-token (th/register-and-login! app {:name "Player" :email "playerstats@test.com" :password "pass123"})
        ;; Fetch statistics (total should be 10 + 2 = 12 goals)
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/statistics"))
                          (mock/query-string {:year 2026})
                          ((th/auth-cookie player-token))))
        body (th/decode-body response)
        stat (first (filter #(= "Player" (:player_name %)) body))]

    (is (= 200 (:status response)))
    (is (some? stat))
    (is (= 12 (:goal stat))) ;; 10 manual + 2 match
    (is (= 5 (:assist stat)))
    (is (= 1 (:own_goal stat)))
    (is (= 1 (:peladas_played stat)))))

(deftest manual-stats-additive-aggregation-test
  (let [app (-> th/*test-system* :app :app-handler)
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
        stat (first (filter #(= "Player" (:player_name %)) body))]

    (is (= 200 (:status response)))
    (is (= 8 (:goal stat)))))
