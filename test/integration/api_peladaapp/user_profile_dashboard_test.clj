(ns integration.api-peladaapp.user-profile-dashboard-test
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

(deftest user-profile-dashboard-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        _ (th/register-and-login! app {:name "Dash Player" :email "dash_player@test.com" :password "pass123"})
        _ (th/register-and-login! app {:name "Dash Voter" :email "dash_voter@test.com" :password "pass123"})
        player-user-id (th/user-id-by-email ds "dash_player@test.com")
        voter-user-id (th/user-id-by-email ds "dash_voter@test.com")

        org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org Dash"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id player-user-id :grade 5.0 :member_type [:cast "mensalista" :member_type]}])))
        _ (exec! ds (-> (h/insert-into :OrganizationPlayers) (h/values [{:organization_id org-id :user_id voter-user-id :grade 5.0 :member_type [:cast "mensalista" :member_type]}])))
        player-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id player-user-id] [:= :organization_id org-id]))))
        voter-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :user_id voter-user-id] [:= :organization_id org-id]))))

        pelada-id (:id (exec-one! ds (-> (h/insert-into :Peladas)
                                         (h/values [{:organization_id org-id
                                                     :location "Arena Dash"
                                                     :scheduled_at [[:cast "2026-09-10 19:00:00" :timestamp]]
                                                     :status [:cast "closed" :pelada_status]}])
                                         (h/returning :id))))
        team-a-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Time A"}]) (h/returning :id))))
        team-b-id (:id (exec-one! ds (-> (h/insert-into :Teams) (h/values [{:pelada_id pelada-id :name "Time B"}]) (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :TeamPlayers) (h/values [{:team_id team-a-id :player_id player-id} {:team_id team-b-id :player_id voter-id}])))
        match-id (:id (exec-one! ds (-> (h/insert-into :Matches)
                                        (h/values [{:pelada_id pelada-id :home_team_id team-a-id :away_team_id team-b-id
                                                    :sequence 1 :home_score 2 :away_score 0 :status [:cast "finished" :match_status]}])
                                        (h/returning :id))))
        _ (exec! ds (-> (h/insert-into :MatchEvents)
                        (h/values [{:match_id match-id :player_id player-id :event_type [:cast "goal" :match_event_type]}
                                   {:match_id match-id :player_id player-id :event_type [:cast "assist" :match_event_type]}])))
        _ (exec! ds (-> (h/insert-into :Votes)
                        (h/values [{:pelada_id pelada-id :voter_id voter-id :target_id player-id :stars 5}])))
        token (th/register-and-login! app {:name "Dash Player" :email "dash_player@test.com" :password "pass123"})
        response (app (-> (mock/request :get (str "/api/user/" player-user-id "/profile-dashboard"))
                          (mock/query-string {:year 2026})
                          ((th/auth-cookie token))))
        body (th/decode-body response)
        summary (:summary body)]
    (testing "summary reflects the real season results"
      (is (= 200 (:status response)))
      (is (= 2026 (:year body)))
      (is (= 1 (:matches_played summary)))
      (is (= 1 (:goals summary)))
      (is (= 1 (:assists summary)))
      (is (= 1 (:titles summary)))
      (is (= 1 (:mvp_count summary)))
      (is (= 10.0 (:avg_rating summary)) "A single 5-star vote maps to a 10 performance score")
      (is (= 5.0 (:avg_stars summary)))
      (is (nil? (:attendance_rate summary)) "No attendance records means no rate yet")
      (is (= "Org Dash" (:organization_name (first (:groups body)))))
      (is (= 1 (get-in body [:skills :ratings_count]))))
    (testing "recent peladas carry the requesting user's own line"
      (is (= "Time A" (get-in body [:recent_peladas 0 :user :team_name])))
      (is (= 1 (get-in body [:recent_peladas 0 :user :team_position])))
      (is (= "Org Dash" (get-in body [:recent_peladas 0 :organization_name]))))
    (testing "presence marks the pelada week as no_game when nobody recorded attendance"
      (is (= [{:week_start "2026-09-07" :status "no_game"}] (:presence body))))))

(deftest user-profile-dashboard-presence-and-attendance-rate-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Dash Rate" :email "dash_rate@test.com" :password "pass123"})
        user-id (th/user-id-by-email ds "dash_rate@test.com")
        org-id (parse-uuid (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                                         (mock/json-body {:name "Org Rate"})
                                                         (th/auth-cookie token))))))
        player-id (th/player-id-by-user-id ds user-id org-id)
        mk-pelada (fn [scheduled-at]
                    (:id (exec-one! ds (-> (h/insert-into :Peladas)
                                           (h/values [{:organization_id org-id
                                                       :scheduled_at [[:cast scheduled-at :timestamp]]
                                                       :status [:cast "closed" :pelada_status]}])
                                           (h/returning :id)))))
        declined-pelada (mk-pelada "2026-08-05 19:00:00")
        confirmed-pelada (mk-pelada "2026-09-10 19:00:00")
        _ (mk-pelada "2026-09-16 19:00:00")
        _ (exec! ds (-> (h/insert-into :Attendance)
                        (h/values [{:pelada_id declined-pelada :player_id player-id
                                    :status [:cast "declined" :attendance_status]}
                                   {:pelada_id confirmed-pelada :player_id player-id
                                    :status [:cast "confirmed" :attendance_status]}])))
        response (app (-> (mock/request :get (str "/api/user/" user-id "/profile-dashboard"))
                          (mock/query-string {:year 2026})
                          (th/auth-cookie token)))
        body (th/decode-body response)]
    (testing "attendance rate is confirmed over decided"
      (is (= 200 (:status response)))
      (is (= 50.0 (get-in body [:summary :attendance_rate]))))
    (testing "presence reports absent / present / no_game per week, oldest first"
      (is (= [{:week_start "2026-08-03" :status "absent"}
              {:week_start "2026-09-07" :status "present"}
              {:week_start "2026-09-14" :status "no_game"}]
             (:presence body))))))

(deftest user-profile-dashboard-empty-state-test
  (let [app (-> th/*test-system* :app :app-handler)
        token (th/register-and-login! app {:name "Dash Empty" :email "dash_empty@test.com" :password "pass123"})
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        user-id (th/user-id-by-email ds "dash_empty@test.com")
        response (app (-> (mock/request :get (str "/api/user/" user-id "/profile-dashboard"))
                          (th/auth-cookie token)))
        body (th/decode-body response)
        summary (:summary body)]
    (testing "a user with no organizations gets a zeroed dashboard"
      (is (= 200 (:status response)))
      (is (= 0 (:matches_played summary)))
      (is (= 0 (:goals summary)))
      (is (= 0 (:assists summary)))
      (is (= 0 (:titles summary)))
      (is (= 0 (:mvp_count summary)))
      (is (= 0 (:garcom_count summary)))
      (is (nil? (:avg_rating summary)))
      (is (nil? (:avg_stars summary)))
      (is (nil? (:attendance_rate summary)))
      (is (= [] (:groups body)))
      (is (= [] (:presence body)))
      (is (= [] (:recent_peladas body)))
      (is (= 0 (get-in body [:skills :ratings_count]))))))

(deftest user-profile-dashboard-access-and-validation-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        self-token (th/register-and-login! app {:name "Dash Target" :email "dash_target@test.com" :password "pass123"})
        target-id (th/user-id-by-email ds "dash_target@test.com")
        other-token (th/register-and-login! app {:name "Dash Other" :email "dash_other@test.com" :password "pass123"})]

    (testing "another user cannot read the dashboard"
      (let [response (app (-> (mock/request :get (str "/api/user/" target-id "/profile-dashboard"))
                              (th/auth-cookie other-token)))]
        (is (= 403 (:status response)))))

    (testing "unauthenticated requests get 401"
      (let [response (app (mock/request :get (str "/api/user/" target-id "/profile-dashboard")))]
        (is (= 401 (:status response)))))

    (testing "invalid year gets 400"
      (let [response (app (-> (mock/request :get (str "/api/user/" target-id "/profile-dashboard"))
                              (mock/query-string {:year "last-tuesday"})
                              (th/auth-cookie self-token)))]
        (is (= 400 (:status response)))))))
