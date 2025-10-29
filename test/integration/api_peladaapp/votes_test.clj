(ns api-peladaapp.votes-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [api-peladaapp.test-helpers :as th])
  (:import [java.time Instant Duration]))

(defn- decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when (not (str/blank? b)) (json/read-str b :key-fn keyword))
      (instance? java.io.InputStream b) (let [s (slurp b)] (when (not (str/blank? s)) (json/read-str s :key-fn keyword)))
      :else nil)))

(deftest votes-and-normalization
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; seed org and players
    (sql/insert! ds :organizations {:name "Org"})
    (doseq [[i name email] [[1 "Ana" "ana@example.com"] [2 "Bob" "bob@example.com"] [3 "Cid" "cid@example.com"]]]
      (sql/insert! ds :users {:name name :email email :password "p"})
      (sql/insert! ds :organizationplayers {:id i :organization_id 1 :user_id i}))
    ;; Create closed pelada with closed_at timestamp
    (let [closed-at (str (.minus (Instant/now) (Duration/ofHours 2)))]
      (sql/insert! ds :peladas {:id 1 :organization_id 1 :scheduled_at "2025-10-28" :status "closed" :closed_at closed-at}))

    ;; auth-protected endpoints: login to get token
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Admin" :email "admin@example.com" :password "p"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "admin@example.com" :password "p"})))
          token (:token (decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))]
      ;; cast votes: 2 and 3 vote for 1 (no self-vote)
      (is (= 201 (:status (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 1 :voter_id 2 :target_id 1 :stars 5}))))))
      (is (= 201 (:status (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 1 :voter_id 3 :target_id 1 :stars 3}))))))

      ;; list votes
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
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; seed org, players, and teams
    (sql/insert! ds :organizations {:name "Org"})
    (doseq [[i name email] [[1 "Ana" "ana@example.com"] [2 "Bob" "bob@example.com"] [3 "Cid" "cid@example.com"] [4 "Dan" "dan@example.com"]]]
      (sql/insert! ds :users {:name name :email email :password "p"})
      (sql/insert! ds :organizationplayers {:id i :organization_id 1 :user_id i}))
    
    ;; Create closed pelada with closed_at timestamp
    (let [closed-at (str (.minus (Instant/now) (Duration/ofHours 1)))]
      (sql/insert! ds :peladas {:id 1 :organization_id 1 :scheduled_at "2025-10-28" :status "closed" :closed_at closed-at}))
    
    ;; Create teams and add players
    (sql/insert! ds :teams {:id 1 :pelada_id 1 :name "Team A"})
    (sql/insert! ds :teams {:id 2 :pelada_id 1 :name "Team B"})
    (sql/insert! ds :teamplayers {:team_id 1 :player_id 1})
    (sql/insert! ds :teamplayers {:team_id 1 :player_id 2})
    (sql/insert! ds :teamplayers {:team_id 2 :player_id 3})
    (sql/insert! ds :teamplayers {:team_id 2 :player_id 4})

    ;; auth-protected endpoints: login to get token
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Admin" :email "admin@example.com" :password "p"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "admin@example.com" :password "p"})))
          token (:token (decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))]
      
      (testing "Voting info before voting"
        (let [resp (app (-> (mock/request :get "/api/peladas/1/voters/1/voting-info") auth))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (true? (:can_vote body)))
          (is (false? (:has_voted body)))
          ;; Player 1 should see players 2, 3, 4 (not themselves)
          (is (= 3 (count (:eligible_players body))))))
      
      (testing "Batch cast votes"
        (let [votes [{:target_id 2 :stars 5}
                     {:target_id 3 :stars 4}
                     {:target_id 4 :stars 3}]
              resp (app (-> (mock/request :post "/api/peladas/1/votes/batch") 
                           auth 
                           (mock/json-body {:voter_id 1 :votes votes})))
              body (decode-body resp)]
          (is (= 200 (:status resp)))
          (is (= 3 (:votes-cast body)))))
      
      (testing "Voting info after voting"
        (let [resp (app (-> (mock/request :get "/api/peladas/1/voters/1/voting-info") auth))
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
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; seed org and players
    (sql/insert! ds :organizations {:name "Org"})
    (doseq [[i name email] [[1 "Ana" "ana@example.com"] [2 "Bob" "bob@example.com"]]]
      (sql/insert! ds :users {:name name :email email :password "p"})
      (sql/insert! ds :organizationplayers {:id i :organization_id 1 :user_id i}))
    
    ;; auth-protected endpoints: login to get token
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Admin" :email "admin@example.com" :password "p"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "admin@example.com" :password "p"})))
          token (:token (decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))]
      
      (testing "Cannot vote on open pelada"
        (sql/insert! ds :peladas {:id 2 :organization_id 1 :scheduled_at "2025-10-28" :status "open"})
        (let [resp (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 2 :voter_id 2 :target_id 1 :stars 5})))]
          (is (= 400 (:status resp)))))
      
      (testing "Cannot vote after 24 hours"
        (let [twenty-five-hours-ago (str (.minus (Instant/now) (Duration/ofHours 25)))]
          (sql/insert! ds :peladas {:id 3 :organization_id 1 :scheduled_at "2025-10-28" :status "closed" :closed_at twenty-five-hours-ago})
          (let [resp (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 3 :voter_id 2 :target_id 1 :stars 5})))]
            (is (= 400 (:status resp))))))
      
      (testing "Can vote within 24 hours"
        (let [two-hours-ago (str (.minus (Instant/now) (Duration/ofHours 2)))]
          (sql/insert! ds :peladas {:id 4 :organization_id 1 :scheduled_at "2025-10-28" :status "closed" :closed_at two-hours-ago})
          (let [resp (app (-> (mock/request :post "/api/votes") auth (mock/json-body {:pelada_id 4 :voter_id 2 :target_id 1 :stars 5})))]
            (is (= 201 (:status resp)))))))))
