(ns api-peladaapp.votes-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [ring.mock.request :as mock])
  (:import
   [java.time Duration Instant]))

(use-fixtures :each th/test-system-fixture)

(defn- decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when (not (str/blank? b)) (json/read-str b :key-fn keyword))
      (instance? java.io.InputStream b) (let [s (slurp b)] (when (not (str/blank? s)) (json/read-str s :key-fn keyword)))
      :else nil)))

(defn- register-and-login [app email name]
  (app (-> (mock/request :post "/auth/register")
           (mock/json-body {:name name :email email :password "p"})))
  (let [login-resp (app (-> (mock/request :post "/auth/login")
                            (mock/json-body {:email email :password "p"})))
        body (decode-body login-resp)]
    {:token (:token body)
     :user-id (:id (:user body))}))

(deftest votes-and-normalization
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; seed org
    (sql/insert! ds :Organizations {:name "Org"})
    
    ;; Register users via API to ensure proper auth state
    (let [{ana-token :token ana-user-id :user-id} (register-and-login app "ana@example.com" "Ana")
          {bob-user-id :user-id} (register-and-login app "bob@example.com" "Bob")
          {cid-user-id :user-id} (register-and-login app "cid@example.com" "Cid")
          auth (fn [req] (mock/header req "authorization" (str "Token " ana-token)))]
      
      ;; Map them to organization players
      (sql/insert! ds :OrganizationPlayers {:id 1 :organization_id 1 :user_id ana-user-id})
      (sql/insert! ds :OrganizationPlayers {:id 2 :organization_id 1 :user_id bob-user-id})
      (sql/insert! ds :OrganizationPlayers {:id 3 :organization_id 1 :user_id cid-user-id})

      ;; Create closed pelada with closed_at timestamp
      (let [closed-at (str (.minus (Instant/now) (Duration/ofHours 2)))]
        (sql/insert! ds :Peladas {:id 1 :organization_id 1 :scheduled_at "2025-10-28" :status "closed" :closed_at closed-at}))

      ;; cast votes: 2 and 3 vote for 1 (no self-vote)
      ;; We need Bob and Cid tokens to vote for Ana
      (let [{bob-token :token} (register-and-login app "bob@example.com" "Bob")
            {cid-token :token} (register-and-login app "cid@example.com" "Cid")
            bob-auth (fn [req] (mock/header req "authorization" (str "Token " bob-token)))
            cid-auth (fn [req] (mock/header req "authorization" (str "Token " cid-token)))]
        
        (is (= 201 (:status (app (-> (mock/request :post "/api/votes") bob-auth (mock/json-body {:pelada_id 1 :voter_id 2 :target_id 1 :stars 5}))))))
        (is (= 201 (:status (app (-> (mock/request :post "/api/votes") cid-auth (mock/json-body {:pelada_id 1 :voter_id 3 :target_id 1 :stars 3})))))))

      ;; list votes (Ana viewing)
      (let [resp (app (-> (mock/request :get "/api/peladas/1/votes") auth))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count body))))

      ;; normalized score for player 1 should be avg(5,3)=4 => 8
      (let [resp (app (-> (mock/request :get "/api/peladas/1/players/1/normalized-score") auth))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (= 8.0 (:score body))))

      ;; self vote should fail
      (let [resp (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 1 :voter_id 1 :target_id 1 :stars 4})))]
        (is (= 400 (:status resp)))))))

(deftest batch-voting-and-eligibility
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; seed org
    (sql/insert! ds :Organizations {:name "Org"})
    
    ;; Register users
    (let [{ana-token :token ana-user-id :user-id} (register-and-login app "ana@example.com" "Ana")
          {bob-user-id :user-id} (register-and-login app "bob@example.com" "Bob")
          {cid-user-id :user-id} (register-and-login app "cid@example.com" "Cid")
          {dan-user-id :user-id} (register-and-login app "dan@example.com" "Dan")
          auth (fn [req] (mock/header req "authorization" (str "Token " ana-token)))]
      
      (sql/insert! ds :OrganizationPlayers {:id 1 :organization_id 1 :user_id ana-user-id})
      (sql/insert! ds :OrganizationPlayers {:id 2 :organization_id 1 :user_id bob-user-id})
      (sql/insert! ds :OrganizationPlayers {:id 3 :organization_id 1 :user_id cid-user-id})
      (sql/insert! ds :OrganizationPlayers {:id 4 :organization_id 1 :user_id dan-user-id})

      ;; Create closed pelada with closed_at timestamp
      (let [closed-at (str (.minus (Instant/now) (Duration/ofHours 1)))]
        (sql/insert! ds :Peladas {:id 1 :organization_id 1 :scheduled_at "2025-10-28" :status "closed" :closed_at closed-at}))

      ;; Create teams and add players
      (sql/insert! ds :Teams {:id 1 :pelada_id 1 :name "Team A"})
      (sql/insert! ds :Teams {:id 2 :pelada_id 1 :name "Team B"})
      (sql/insert! ds :TeamPlayers {:team_id 1 :player_id 1})
      (sql/insert! ds :TeamPlayers {:team_id 1 :player_id 2})
      (sql/insert! ds :TeamPlayers {:team_id 2 :player_id 3})
      (sql/insert! ds :TeamPlayers {:team_id 2 :player_id 4})

      (testing "Voting info before voting"
        (let [resp (app (-> (mock/request :get "/api/peladas/1/voting-info") auth))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (true? (:can_vote body)))
          (is (false? (:has_voted body)))
          (is (= 1 (:voter_player_id body)))
          ;; Player 1 should see players 2, 3, 4 (not themselves)
          (is (= 3 (count (:eligible_players body))))
          (is (= "Bob" (:name (first (filter #(= 2 (:player_id %)) (:eligible_players body))))))))

      (testing "Batch cast votes"
        (let [votes [{:target_id 2 :stars 5}
                     {:target_id 3 :stars 4}
                     {:target_id 4 :stars 3}]
              resp (app (-> (mock/request :post "/api/peladas/1/votes/batch")
                            auth
                            (mock/json-body {:voter_id 1 :votes votes})))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (= 3 (:votes_cast body)))))

      (testing "Voting info after voting"
        (let [resp (app (-> (mock/request :get "/api/peladas/1/voting-info") auth))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (true? (:can_vote body)))
          (is (true? (:has_voted body)))))

      (testing "Re-voting replaces previous votes"
        (let [new-votes [{:target_id 2 :stars 1}
                         {:target_id 3 :stars 2}
                         {:target_id 4 :stars 3}]
              resp (app (-> (mock/request :post "/api/peladas/1/votes/batch")
                            auth
                            (mock/json-body {:voter_id 1 :votes new-votes})))]
          (is (= 200 (:status resp)))
          ;; Check that votes were replaced
          (let [votes-resp (app (-> (mock/request :get "/api/peladas/1/votes") auth))
                votes-body (decode-body votes-resp)
                player-2-votes (filter #(= 2 (:target_id %)) votes-body)]
            (is (= 1 (:stars (first player-2-votes))))))))))

(deftest voting-window-validation
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; seed org
    (sql/insert! ds :Organizations {:name "Org"})
    
    (let [{ana-token :token ana-user-id :user-id} (register-and-login app "ana@example.com" "Ana")
          {bob-user-id :user-id} (register-and-login app "bob@example.com" "Bob")
          auth (fn [req] (mock/header req "authorization" (str "Token " ana-token)))]
      
      (sql/insert! ds :OrganizationPlayers {:id 1 :organization_id 1 :user_id ana-user-id})
      (sql/insert! ds :OrganizationPlayers {:id 2 :organization_id 1 :user_id bob-user-id})

      (testing "Cannot vote on open pelada"
        (sql/insert! ds :Peladas {:id 2 :organization_id 1 :scheduled_at "2025-10-28" :status "open"})
        (let [resp (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 2 :voter_id 1 :target_id 2 :stars 5})))]
          (is (= 400 (:status resp)))))

      (testing "Cannot vote after 24 hours"
        (let [twenty-five-hours-ago (str (.minus (Instant/now) (Duration/ofHours 25)))]
          (sql/insert! ds :Peladas {:id 3 :organization_id 1 :scheduled_at "2025-10-28" :status "closed" :closed_at twenty-five-hours-ago})
          (let [resp (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 3 :voter_id 1 :target_id 2 :stars 5})))]
            (is (= 400 (:status resp))))))

      (testing "Can vote within 24 hours"
        (let [two-hours-ago (str (.minus (Instant/now) (Duration/ofHours 2)))]
          (sql/insert! ds :Peladas {:id 4 :organization_id 1 :scheduled_at "2025-10-28" :status "closed" :closed_at two-hours-ago})
          (let [resp (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 4 :voter_id 1 :target_id 2 :stars 5})))]
            (is (= 201 (:status resp)))))))))
