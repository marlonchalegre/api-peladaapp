(ns api-peladaapp.matches-test
  (:require [clojure.test :refer [deftest is]]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [api-peladaapp.test-helpers :as th]))

(defn- decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when (not (str/blank? b)) (json/read-str b :key-fn keyword))
      (instance? java.io.InputStream b) (let [s (slurp b)] (when (not (str/blank? s)) (json/read-str s :key-fn keyword)))
      :else nil)))

(deftest matches-flow
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; seed org, pelada and teams
    (sql/insert! ds :organizations {:name "Org"})
    (sql/insert! ds :peladas {:organization_id 1 :scheduled_at "2025-10-28"})
    (doseq [n ["A" "B"]]
      (sql/insert! ds :teams {:pelada_id 1 :name n}))
    ;; auth
    (let [_ (app (-> (mock/request :post "/auth/register")
                     (mock/json-body {:name "Ana" :email "ana@ex.com" :password "p"})))
          login (app (-> (mock/request :post "/auth/login")
                         (mock/json-body {:email "ana@ex.com" :password "p"})))
          token (:token (decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))]
      ;; create matches by beginning pelada (ensures a match exists)
      (doseq [n ["C" "D"]]
        (sql/insert! ds :teams {:pelada_id 1 :name n}))
      (is (= 200 (:status (app (-> (mock/request :post "/api/peladas/1/begin") auth)))))
      ;; update score of first match id=1
      (let [resp (app (-> (mock/request :put "/api/matches/1/score")
                          auth
                          (mock/json-body {:home_score 1 :away_score 0 :status "finished"})))]
        (is (= 200 (:status resp)))))))
