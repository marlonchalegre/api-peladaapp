(ns api-peladaapp.user-profile-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when (not (str/blank? b)) (json/read-str b :key-fn keyword))
      (instance? java.io.InputStream b) (let [s (slurp b)] (when (not (str/blank? s)) (json/read-str s :key-fn keyword)))
      :else nil)))

(deftest get-user-profile-success
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        token (th/register-and-login! app {:name "Profile User" :email "profile@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "profile@example.com")
        resp (app (-> (mock/request :get (str "/api/user/" user-id))
                      (mock/cookie "authToken" token)))
        body (decode-body resp)]
    (is (= 200 (:status resp)))
    (is (= "Profile User" (:name body)))
    (is (= "profile@example.com" (:email body)))))

(deftest get-user-profile-unauthorized
  (let [app (-> th/*test-system* :app :app-handler)
        token (th/register-and-login! app {:name "Any" :email "any@any.com" :password "any"})
        resp (app (-> (mock/request :get "/api/user/9999")
                      (mock/cookie "authToken" token)))]
    (is (= 403 (:status resp)))))

(deftest update-user-profile-success
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        token (th/register-and-login! app {:name "Old Name" :email "old@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "old@example.com")
        resp (app (-> (mock/request :put (str "/api/user/" user-id "/profile"))
                      (mock/cookie "authToken" token)
                      (mock/json-body {:name "New Name" :position "Midfielder"})))
        body (decode-body resp)]
    (is (= 200 (:status resp)))
    (is (= "New Name" (:name body)))
    (is (= "Midfielder" (:position body)))))

(deftest delete-user-success
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        token (th/register-and-login! app {:name "Delete Me" :email "delete@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "delete@example.com")
        resp (app (-> (mock/request :delete (str "/api/user/" user-id))
                      (mock/cookie "authToken" token)))]
    (is (= 204 (:status resp)))
    (is (nil? (th/user-id-by-email ds "delete@example.com")))))

(deftest update-user-profile-duplicate-email
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        ;; Register first user
        _ (th/register-and-login! app {:name "User 1" :username "user1" :email "user1@example.com" :password "pass"})
        ;; Register second user
        token2 (th/register-and-login! app {:name "User 2" :username "user2" :email "user2@example.com" :password "pass"})
        user2-id (th/user-id-by-email ds "user2@example.com")
        ;; Try to update second user's email to first user's email
        resp (app (-> (mock/request :put (str "/api/user/" user2-id "/profile"))
                      (mock/cookie "authToken" token2)
                      (mock/json-body {:email "user1@example.com"})))
        body (decode-body resp)]
    (is (= 400 (:status resp)))
    (is (= "Email already exists" (:message body)))))

(deftest update-user-profile-non-conflicting-checks
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        ;; Create two users
        token1 (th/register-and-login! app {:name "User A" :username "usera" :email "usera@example.com" :password "pass"})
        usera-id (th/user-id-by-email ds "usera@example.com")
        token2 (th/register-and-login! app {:name "User B" :username "userb" :email "userb@example.com" :password "pass"})
        userb-id (th/user-id-by-email ds "userb@example.com")]

    (testing "Updating own phone number without changing email (case-sensitive comparison should not block)"
      (let [resp (app (-> (mock/request :put (str "/api/user/" usera-id "/profile"))
                          (mock/cookie "authToken" token1)
                          (mock/json-body {:email "usera@example.com" :phone "12345"})))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= "12345" (:phone body)))
        (is (= "usera@example.com" (:email body)))))

    (testing "Updating own phone number with email casing mismatch should not trigger duplication error"
      (let [resp (app (-> (mock/request :put (str "/api/user/" usera-id "/profile"))
                          (mock/cookie "authToken" token1)
                          (mock/json-body {:email "USERA@example.com" :phone "54321"})))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= "54321" (:phone body)))))

    (testing "Updating one user's phone while sending blank email should not conflict with another user who has blank email"
      ;; First, set User A's email to blank (which will be stored as nil)
      (let [resp-a (app (-> (mock/request :put (str "/api/user/" usera-id "/profile"))
                            (mock/cookie "authToken" token1)
                            (mock/json-body {:email "" :phone "111"})))
            body-a (decode-body resp-a)]
        (is (= 200 (:status resp-a)))
        (is (nil? (:email body-a))))

      ;; Now try to update User B's phone while sending empty email
      (let [resp-b (app (-> (mock/request :put (str "/api/user/" userb-id "/profile"))
                            (mock/cookie "authToken" token2)
                            (mock/json-body {:email "" :phone "222"})))
            body-b (decode-body resp-b)]
        (is (= 200 (:status resp-b)))
        (is (= "222" (:phone body-b)))
        (is (nil? (:email body-b)))))))

(deftest reset-password-success
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        ;; Register normal user
        token (th/register-and-login! app {:name "Self User" :email "self@example.com" :password "pass123"})
        user-id (th/user-id-by-email ds "self@example.com")

        ;; 1. User changes their own password
        resp1 (app (-> (mock/request :post (str "/api/user/" user-id "/reset-password"))
                       (mock/cookie "authToken" token)
                       (mock/json-body {:password "newpass123"})))]
    (is (= 200 (:status resp1)))

    ;; Verify they can login with new password
    (let [login-resp (app (-> (mock/request :post "/auth/login")
                              (mock/json-body {:email "self@example.com" :password "newpass123"})))
          login-body (th/decode-body login-resp)]
      (is (= 200 (:status login-resp)))
      (is (some? (:token login-body))))))

(deftest reset-password-as-admin
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        ;; Register normal user
        _ (th/register-and-login! app {:name "Target User" :email "target@example.com" :password "pass123"})
        target-id (th/user-id-by-email ds "target@example.com")

        ;; Register admin user and promote
        _ (th/register-and-login! app {:name "Admin User" :email "admin@example.com" :password "pass123"})
        admin-id (th/user-id-by-email ds "admin@example.com")
        _ (jdbc/execute! ds [(str "UPDATE \"Users\" SET is_super_admin = true WHERE id = '" admin-id "'")])
        ;; Re-login to generate a new token with the updated is_super_admin claim
        admin-token (let [login-resp (app (-> (mock/request :post "/auth/login")
                                              (mock/json-body {:email "admin@example.com" :password "pass123"})))
                          login-body (th/decode-body login-resp)]
                      (:token login-body))

        ;; Reset user's password as admin
        resp (app (-> (mock/request :post (str "/api/user/" target-id "/reset-password"))
                      (mock/cookie "authToken" admin-token)
                      (mock/json-body {:password "adminpass123"})))]
    (is (= 200 (:status resp)))

    ;; Verify target user can login with new password
    (let [login-resp (app (-> (mock/request :post "/auth/login")
                              (mock/json-body {:email "target@example.com" :password "adminpass123"})))
          login-body (th/decode-body login-resp)]
      (is (= 200 (:status login-resp)))
      (is (some? (:token login-body))))))

(deftest reset-password-unauthorized
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        ;; Register user 1
        _ (th/register-and-login! app {:name "User 1" :email "u1@example.com" :password "pass123"})
        u1-id (th/user-id-by-email ds "u1@example.com")

        ;; Register user 2
        u2-token (th/register-and-login! app {:name "User 2" :email "u2@example.com" :password "pass123"})

        ;; Try to change user 1's password using user 2's token
        resp (app (-> (mock/request :post (str "/api/user/" u1-id "/reset-password"))
                      (mock/cookie "authToken" u2-token)
                      (mock/json-body {:password "hackedpass123"})))]
    (is (= 403 (:status resp)))))

(deftest delete-user-cascade-success
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        ;; Register user
        token (th/register-and-login! app {:name "Cascade User" :email "cascade@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "cascade@example.com")

        ;; Create organization (owner is user-id)
        org-resp (app (-> (mock/request :post "/api/organizations")
                          (mock/cookie "authToken" token)
                          (mock/json-body {:name "Cascade Org"})))
        org-body (th/decode-body org-resp)
        org-id (parse-uuid (:id org-body))

        ;; User automatically becomes player. Let's query to find their organization player ID.
        player-id (th/player-id-by-user-id ds user-id org-id)

        ;; Create a Pelada in this organization
        pelada-resp (app (-> (mock/request :post "/api/peladas")
                             (mock/cookie "authToken" token)
                             (mock/json-body {:organization_id (str org-id)
                                              :name "Pelada Cascade"
                                              :start_time "2026-05-26T18:00:00Z"
                                              :duration_minutes 60
                                              :location "Field 1"
                                              :max_players 10})))
        pelada-body (th/decode-body pelada-resp)
        pelada-id (parse-uuid (:id pelada-body))

        ;; Add attendance for this player
        _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
                   (mock/cookie "authToken" token)
                   (mock/json-body {:status "confirmed"})))

        ;; Verify attendance exists before deletion
        att-exists? (some? (jdbc/execute-one! ds [(str "SELECT 1 FROM \"Attendance\" WHERE player_id = '" player-id "'")]))
        _ (is (true? att-exists?))

        ;; Insert a password reset token
        _ (jdbc/execute! ds ["INSERT INTO password_reset_tokens (user_id, token, expires_at) VALUES (?, ?, CURRENT_TIMESTAMP + interval '1 hour')" user-id "cascade-token"])
        token-exists? (some? (jdbc/execute-one! ds [(str "SELECT 1 FROM password_reset_tokens WHERE user_id = '" user-id "'")]))
        _ (is (true? token-exists?))

        ;; Delete user
        del-resp (app (-> (mock/request :delete (str "/api/user/" user-id))
                          (mock/cookie "authToken" token)))]
    (is (= 204 (:status del-resp)))
    ;; Verify user is deleted
    (is (nil? (th/user-id-by-email ds "cascade@example.com")))
    ;; Verify organization owner is set to NULL
    (is (nil? (:owner_id (jdbc/execute-one! ds [(str "SELECT owner_id FROM \"Organizations\" WHERE id = '" org-id "'")]))))
    ;; Verify player record is cascaded/deleted
    (is (nil? (jdbc/execute-one! ds [(str "SELECT 1 FROM \"OrganizationPlayers\" WHERE id = '" player-id "'")])))
    ;; Verify attendance record is cascaded/deleted
    (is (nil? (jdbc/execute-one! ds [(str "SELECT 1 FROM \"Attendance\" WHERE player_id = '" player-id "'")])))
    ;; Verify token is cascaded/deleted
    (is (nil? (jdbc/execute-one! ds [(str "SELECT 1 FROM password_reset_tokens WHERE user_id = '" user-id "'")])))))

(deftest get-user-profile-includes-stats
  (let [app (-> th/*test-system* :app :app-handler)
        ds (api-peladaapp.test-helpers/get-test-datasource)

        token (th/register-and-login! app {:name "Stats User" :email "stats@example.com" :password "pass"})
        user-id (th/user-id-by-email ds "stats@example.com")

        ;; Verify default stats are 0
        resp1 (app (-> (mock/request :get (str "/api/user/" user-id))
                       (mock/cookie "authToken" token)))
        body1 (decode-body resp1)]
    (is (= 200 (:status resp1)))
    (is (= {:goals 0 :assists 0 :matches 0} (:stats body1)))

    ;; Now let's insert some mock manual stats and a completed match attendance
    (let [org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/cookie "authToken" token)
                            (mock/json-body {:name "Stats Org"})))
          org-body (th/decode-body org-resp)
          org-id (parse-uuid (:id org-body))
          player-id (th/player-id-by-user-id ds user-id org-id)

          ;; Insert manual stats
          _ (jdbc/execute! ds ["INSERT INTO \"ManualStats\" (organization_id, player_id, year, goals, assists, own_goals) VALUES (?, ?, 2026, 5, 3, 0)" org-id player-id])

          ;; Create a Pelada
          pelada-resp (app (-> (mock/request :post "/api/peladas")
                               (mock/cookie "authToken" token)
                               (mock/json-body {:organization_id (str org-id)
                                                :name "Pelada Stats"
                                                :scheduled_at "2026-06-15T18:00:00Z"
                                                :duration_minutes 60
                                                :location "Stadium"
                                                :max_players 10})))
          pelada-body (th/decode-body pelada-resp)
          pelada-id (parse-uuid (:id pelada-body))

          ;; Add attendance
          _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
                     (mock/cookie "authToken" token)
                     (mock/json-body {:status "confirmed"})))

          ;; Close the pelada
          _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close"))
                     (mock/cookie "authToken" token)))

          ;; Fetch user profile again
          resp2 (app (-> (mock/request :get (str "/api/user/" user-id))
                         (mock/cookie "authToken" token)))
          body2 (decode-body resp2)]
      (is (= 200 (:status resp2)))
      (is (= 5 (get-in body2 [:stats :goals])))
      (is (= 3 (get-in body2 [:stats :assists])))
      (is (= 1 (get-in body2 [:stats :matches]))))))

