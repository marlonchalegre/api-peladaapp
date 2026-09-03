(ns api-peladaapp.match-edit-test
  (:require
   [api-peladaapp.server :as server]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
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
        match-id (-> dashboard :matches first :id)
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
        (is (= 403 (:status resp)) "Non-admin should be forbidden")))

    (testing "Simultaneous goals and assists linkage and update/delete collision resistance"
      (th/register-and-login! app {:name "Player 1" :email "p1@test.com" :password "pass"})
      (th/register-and-login! app {:name "Player 2" :email "p2@test.com" :password "pass"})

      (let [p-admin-id (th/player-id-by-user-id db (th/user-id-by-email db "admin@test.com") org-id)
            p1-uid (th/user-id-by-email db "p1@test.com")
            p2-uid (th/user-id-by-email db "p2@test.com")
            p1-id (java.util.UUID/randomUUID)
            p2-id (java.util.UUID/randomUUID)]

        (next.jdbc/execute! db ["INSERT INTO \"OrganizationPlayers\" (id, organization_id, user_id) VALUES (?, ?::uuid, ?::uuid), (?, ?::uuid, ?::uuid)"
                                p1-id org-id p1-uid
                                p2-id org-id p2-uid])

        (let [teams-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth))
              teams-data (th/decode-body teams-resp)
              team-a-id (parse-uuid (-> teams-data :teams first :id))]

          (next.jdbc/execute! db ["INSERT INTO \"TeamPlayers\" (team_id, player_id) VALUES (?, ?), (?, ?), (?, ?)"
                                  team-a-id p-admin-id
                                  team-a-id p1-id
                                  team-a-id p2-id])

          (next.jdbc/execute! db ["INSERT INTO \"MatchLineups\" (match_id, team_id, player_id, is_goalkeeper) VALUES (?::uuid, ?, ?, false), (?::uuid, ?, ?, false), (?::uuid, ?, ?, false)"
                                  match-id team-a-id p-admin-id
                                  match-id team-a-id p1-id
                                  match-id team-a-id p2-id])

          (let [goal1-resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                                    (mock/json-body {:player_id (str p-admin-id)
                                                     :event_type "goal"
                                                     :session_time_ms 1000
                                                     :match_time_ms 1000
                                                     :assistant_id (str p1-id)})
                                    auth))
                _ (is (= 200 (:status goal1-resp)))
                goal1 (th/decode-body goal1-resp)
                goal1-id (java.util.UUID/fromString (:id goal1))

                goal2-resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                                    (mock/json-body {:player_id (str p-admin-id)
                                                     :event_type "goal"
                                                     :session_time_ms 1000
                                                     :match_time_ms 1000
                                                     :assistant_id (str p2-id)})
                                    auth))
                _ (is (= 200 (:status goal2-resp)))
                goal2 (th/decode-body goal2-resp)
                goal2-id (java.util.UUID/fromString (:id goal2))

                events-in-db (jdbc/execute! db ["SELECT id, event_type::text, parent_event_id FROM \"MatchEvents\" WHERE match_id = ?::uuid" match-id] {:builder-fn rs/as-unqualified-lower-maps})

                assist-for-goal1 (first (filter (fn [e] (= (:parent_event_id e) goal1-id)) events-in-db))
                assist-for-goal2 (first (filter (fn [e] (= (:parent_event_id e) goal2-id)) events-in-db))]

            (is (some? assist-for-goal1) "Assist 1 should point to Goal 1")
            (is (some? assist-for-goal2) "Assist 2 should point to Goal 2")
            (is (not= (:id assist-for-goal1) (:id assist-for-goal2)) "Assists should be distinct")

            (let [update-resp (app (-> (mock/request :put (str "/api/matches/" match-id "/events/" goal1-id))
                                       (mock/json-body {:player_id (str p-admin-id) :assistant_id (str p2-id)})
                                       auth))
                  _ (is (= 200 (:status update-resp)))

                  updated-events (jdbc/execute! db ["SELECT id, player_id, parent_event_id FROM \"MatchEvents\" WHERE parent_event_id = ?::uuid" goal1-id] {:builder-fn rs/as-unqualified-lower-maps})
                  updated-assist (first updated-events)]
              (is (= (:player_id updated-assist) p2-id) "Assist 1 player should be updated to p2-id")

              (let [goal2-assist-db (jdbc/execute-one! db ["SELECT id, player_id FROM \"MatchEvents\" WHERE parent_event_id = ?::uuid" goal2-id] {:builder-fn rs/as-unqualified-lower-maps})]
                (is (= (:player_id goal2-assist-db) p2-id) "Goal 2's assist should remain unchanged")))

            (let [delete-resp (app (-> (mock/request :delete (str "/api/matches/" match-id "/events"))
                                       (mock/json-body {:player_id (str p-admin-id) :event_type "goal" :id (str goal1-id)})
                                       auth))
                  _ (is (= 200 (:status delete-resp)))

                  g1-db (jdbc/execute-one! db ["SELECT id FROM \"MatchEvents\" WHERE id = ?::uuid" goal1-id] {:builder-fn rs/as-unqualified-lower-maps})
                  g1-assist-db (jdbc/execute-one! db ["SELECT id FROM \"MatchEvents\" WHERE parent_event_id = ?::uuid" goal1-id] {:builder-fn rs/as-unqualified-lower-maps})

                  g2-db (jdbc/execute-one! db ["SELECT id FROM \"MatchEvents\" WHERE id = ?::uuid" goal2-id] {:builder-fn rs/as-unqualified-lower-maps})
                  g2-assist-db (jdbc/execute-one! db ["SELECT id FROM \"MatchEvents\" WHERE parent_event_id = ?::uuid" goal2-id] {:builder-fn rs/as-unqualified-lower-maps})]

              (is (nil? g1-db) "Goal 1 should be deleted")
              (is (nil? g1-assist-db) "Goal 1's assist should be cascade deleted")
              (is (some? g2-db) "Goal 2 should still exist")
              (is (some? g2-assist-db) "Goal 2's assist should still exist"))))))

    (testing "Fallback assist cascade deletion by timestamp matching when parent_event_id is nil"
      (let [p-admin-id (th/player-id-by-user-id db (th/user-id-by-email db "admin@test.com") org-id)
            goal-id (java.util.UUID/randomUUID)
            assist-id (java.util.UUID/randomUUID)
            session-time 5555
            match-time 2222]
        ;; Insert legacy goal and assist without parent_event_id
        (jdbc/execute! db ["INSERT INTO \"MatchEvents\" (id, match_id, player_id, event_type, session_time_ms, match_time_ms) VALUES (?::uuid, ?::uuid, ?::uuid, 'goal', ?, ?)"
                           goal-id match-id p-admin-id session-time match-time])
        (jdbc/execute! db ["INSERT INTO \"MatchEvents\" (id, match_id, player_id, event_type, session_time_ms, match_time_ms) VALUES (?::uuid, ?::uuid, ?::uuid, 'assist', ?, ?)"
                           assist-id match-id p-admin-id session-time match-time])

        ;; Delete goal
        (let [delete-resp (app (-> (mock/request :delete (str "/api/matches/" match-id "/events"))
                                   (mock/json-body {:player_id (str p-admin-id) :event_type "goal" :id (str goal-id)})
                                   auth))]
          (is (= 200 (:status delete-resp)))
          (let [goal-after (jdbc/execute-one! db ["SELECT id FROM \"MatchEvents\" WHERE id = ?::uuid" goal-id] {:builder-fn rs/as-unqualified-lower-maps})
                assist-after (jdbc/execute-one! db ["SELECT id FROM \"MatchEvents\" WHERE id = ?::uuid" assist-id] {:builder-fn rs/as-unqualified-lower-maps})]
            (is (nil? goal-after) "Legacy goal should be deleted")
            (is (nil? assist-after) "Associated assist should be deleted via timestamp fallback in find-associated-assist")))))

    (testing "Match timer status transitions and idempotency"
      ;; Create a fresh scheduled match
      (let [t-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/matches"))
                            (mock/json-body {:sequence 99})
                            auth))
            new-match-id (:id (th/decode-body t-resp))]
        (when new-match-id
          ;; Verify initial status is scheduled
          (let [m-init (jdbc/execute-one! db ["SELECT status, timer_status FROM \"Matches\" WHERE id = ?::uuid" new-match-id] {:builder-fn rs/as-unqualified-lower-maps})]
            (is (= "scheduled" (:status m-init)))
            (is (= "stopped" (:timer_status m-init))))

          ;; Start timer -> status becomes running and timer_status becomes running
          (let [start-resp (app (-> (mock/request :post (str "/api/matches/" new-match-id "/timer/start")) auth))
                _ (is (= 200 (:status start-resp)))
                m-running (jdbc/execute-one! db ["SELECT status, timer_status, timer_started_at FROM \"Matches\" WHERE id = ?::uuid" new-match-id] {:builder-fn rs/as-unqualified-lower-maps})
                first-started-at (:timer_started_at m-running)]
            (is (= "running" (:status m-running)))
            (is (= "running" (:timer_status m-running)))
            (is (some? first-started-at))

            ;; Idempotent call to start when already running should not change timer_started_at
            (let [start2-resp (app (-> (mock/request :post (str "/api/matches/" new-match-id "/timer/start")) auth))
                  _ (is (= 200 (:status start2-resp)))
                  m-running2 (jdbc/execute-one! db ["SELECT status, timer_status, timer_started_at FROM \"Matches\" WHERE id = ?::uuid" new-match-id] {:builder-fn rs/as-unqualified-lower-maps})]
              (is (= first-started-at (:timer_started_at m-running2)))

              ;; Pause timer -> timer_status becomes paused, status remains running
              (let [pause-resp (app (-> (mock/request :post (str "/api/matches/" new-match-id "/timer/pause")) auth))
                    _ (is (= 200 (:status pause-resp)))
                    m-paused (jdbc/execute-one! db ["SELECT status, timer_status FROM \"Matches\" WHERE id = ?::uuid" new-match-id] {:builder-fn rs/as-unqualified-lower-maps})]
                (is (= "running" (:status m-paused)))
                (is (= "paused" (:timer_status m-paused)))

                ;; Start again from paused -> resumes timer, status remains running
                (let [resume-resp (app (-> (mock/request :post (str "/api/matches/" new-match-id "/timer/start")) auth))
                      _ (is (= 200 (:status resume-resp)))
                      m-resumed (jdbc/execute-one! db ["SELECT status, timer_status FROM \"Matches\" WHERE id = ?::uuid" new-match-id] {:builder-fn rs/as-unqualified-lower-maps})]
                  (is (= "running" (:status m-resumed)))
                  (is (= "running" (:timer_status m-resumed))))))))))))
