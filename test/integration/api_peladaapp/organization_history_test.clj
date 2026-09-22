(ns integration.api-peladaapp.organization-history-test
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.string :as str]
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

(deftest organization-history-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        _ (th/register-and-login! app {:name "History Player" :email "history_player@test.com" :password "pass123"})
        _ (th/register-and-login! app {:name "History Voter" :email "history_voter@test.com" :password "pass123"})
        player-user-id (th/user-id-by-email ds "history_player@test.com")
        voter-user-id (th/user-id-by-email ds "history_voter@test.com")

        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org History"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id player-user-id :grade 5.0 :member_type [:cast "mensalista" :member_type]}])))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id voter-user-id :grade 5.0 :member_type [:cast "mensalista" :member_type]}])))
        player-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id player-user-id] [:= :organization_id org-id]))))
        voter-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id voter-user-id] [:= :organization_id org-id]))))

        pelada-id (:id (exec-one! ds (-> (h/insert-into :Peladas)
                                         (h/values [{:organization_id org-id
                                                     :location "Arena Teste"
                                                     :scheduled_at [[:cast "2026-09-10 19:00:00" :timestamp]]
                                                     :status [:cast "closed" :pelada_status]}])
                                         (h/returning :id))))
        team-a-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Time A"}]) (h/returning :id))))
        team-b-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Time B"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :TeamPlayers) (h/values [{:team_id team-a-id :player_id player-id} {:team_id team-b-id :player_id voter-id}])))
        match-id (:id (exec-one! ds (-> (h/insert-into :Matches)
                                        (h/values [{:pelada_id pelada-id :home_team_id team-a-id :away_team_id team-b-id
                                                    :sequence 1 :home_score 3 :away_score 1 :status [:cast "finished" :match_status]}])
                                        (h/returning :id))))]
    (exec! ds (-> (h/insert-into :MatchEvents)
                  (h/values [{:match_id match-id :player_id player-id :event_type [:cast "goal" :match_event_type]}
                             {:match_id match-id :player_id player-id :event_type [:cast "assist" :match_event_type]}])))
    (exec! ds (-> (h/insert-into :Votes)
                  (h/values [{:pelada_id pelada-id :voter_id voter-id :target_id player-id :stars 5}])))

    (let [app (-> th/*test-system* :app :app-handler)
          db-val (-> th/*test-system* :database :database)
          ds (if (fn? db-val) (db-val) db-val)
          org-id (:id (exec-one! ds (-> (h/select :id) (h/from :Organizations) (h/where [:= :name "Org History"]))))
          token (th/register-and-login! app {:name "History Player" :email "history_player@test.com" :password "pass123"})
          response (app (-> (mock/request :get (str "/api/organizations/" org-id "/history"))
                            (mock/query-string {:year 2026})
                            ((th/auth-cookie token))))
          body (th/decode-body response)
          entry (first body)
          user-line (:user entry)]
      (testing "history returns the closed pelada with its real summary"
        (is (= 200 (:status response)))
        (is (= 1 (count body)))
        (is (= "Arena Teste" (:location entry)))
        (is (= 1 (:matches_count entry)))
        (is (= 2 (:players_count entry)))
        (is (= "Time A" (:champion_team_name entry))))
      (testing "the requesting user sees their own team position and scouts"
        (is (= "Time A" (:team_name user-line)))
        (is (= 1 (:team_position user-line)))
        (is (= 1 (:goals user-line)))
        (is (= 1 (:assists user-line)))
        (is (true? (:is_mvp user-line)))))))

(deftest organization-history-empty-test
  (let [app (-> th/*test-system* :app :app-handler)
        token (th/register-and-login! app {:name "Empty History" :email "history_empty@test.com" :password "pass123"})
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/json-body {:name "Org Without Peladas"})
                          (th/auth-cookie token)))
        org-id (:id (th/decode-body org-resp))
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/history"))
                          (th/auth-cookie token)))]
    (testing "an organization with no closed peladas returns an empty history"
      (is (= 200 (:status response)))
      (is (= [] (th/decode-body response))))))

(deftest organization-history-unrostered-member-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Bench Member" :email "hist_bench@test.com" :password "pass123"})
        org-id (parse-uuid (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                                         (mock/json-body {:name "Org Bench"})
                                                         (th/auth-cookie token))))))
        pelada-id (:id (exec-one! ds (-> (h/insert-into :Peladas)
                                         (h/values [{:organization_id org-id
                                                     :location "Arena Bench"
                                                     :scheduled_at [[:cast "2026-04-10 19:00:00" :timestamp]]
                                                     :status [:cast "closed" :pelada_status]}])
                                         (h/returning :id))))
        team-a (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Only Team"}]) (h/returning :id))))
        team-b (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Ghost Team"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :Matches)
                        (h/values [{:pelada_id pelada-id :home_team_id team-a :away_team_id team-b
                                    :sequence 1 :home_score 1 :away_score 1
                                    :status [:cast "finished" :match_status]}])))
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/history"))
                          (th/auth-cookie token)))
        body (th/decode-body response)
        entry (first body)]
    (testing "a member who did not play gets a nil personal line"
      (is (= 200 (:status response)))
      (is (= 1 (count body)))
      (is (nil? (:user entry)))
      (is (= "Arena Bench" (:location entry)))
      (is (= 1 (:matches_count entry)))
      (is (= 0 (:players_count entry))))))

(deftest organization-history-access-and-validation-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        member-token (th/register-and-login! app {:name "Hist Member" :email "hist_member@test.com" :password "pass123"})
        outsider-token (th/register-and-login! app {:name "Hist Outsider" :email "hist_outsider@test.com" :password "pass123"})
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Org History Access"})
                                             (th/auth-cookie member-token)))))
        _ (exec! ds (-> (h/insert-into :Peladas)
                        (h/values [{:organization_id (parse-uuid org-id)
                                    :scheduled_at [[:cast "2025-05-10 19:00:00" :timestamp]]
                                    :status [:cast "closed" :pelada_status]}])))
        _ (exec! ds (-> (h/insert-into :Peladas)
                        (h/values [{:organization_id (parse-uuid org-id)
                                    :scheduled_at [[:cast "2026-05-10 19:00:00" :timestamp]]
                                    :status [:cast "closed" :pelada_status]}])))]

    (testing "non-members get 403"
      (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/history"))
                              ((th/auth-cookie outsider-token))))]
        (is (= 403 (:status response)))))

    (testing "unauthenticated requests get 401"
      (let [response (app (mock/request :get (str "/api/organizations/" org-id "/history")))]
        (is (= 401 (:status response)))))

    (testing "invalid year format gets 400"
      (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/history"))
                              (mock/query-string {:year "not-a-year"})
                              ((th/auth-cookie member-token))))]
        (is (= 400 (:status response)))))

    (testing "year filter only returns peladas of that year"
      (let [y2026 (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/history"))
                                           (mock/query-string {:year 2026})
                                           ((th/auth-cookie member-token)))))
            y2025 (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/history"))
                                           (mock/query-string {:year 2025})
                                           ((th/auth-cookie member-token)))))
            all-years (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/history"))
                                               ((th/auth-cookie member-token)))))]
        (is (= 1 (count y2026)))
        (is (str/starts-with? (:scheduled_at (first y2026)) "2026"))
        (is (= 1 (count y2025)))
        (is (str/starts-with? (:scheduled_at (first y2025)) "2025"))
        (is (= 2 (count all-years)))))))
