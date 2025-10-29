(ns api-100folego.votes-test
  (:require [clojure.test :refer [deftest is]]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [api-100folego.test-helpers :as th]))

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
    (sql/insert! ds :peladas {:id 1 :organization_id 1 :scheduled_at "2025-10-28"})

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
