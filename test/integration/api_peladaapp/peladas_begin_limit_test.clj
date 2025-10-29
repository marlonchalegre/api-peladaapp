(ns api-peladaapp.peladas-begin-limit-test
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
      (string? b) (when-not (str/blank? b)
                    (try (json/read-str b :key-fn keyword)
                         (catch Exception _ nil)))
      (instance? java.io.InputStream b) (let [s (slurp b)]
                                          (when-not (str/blank? s)
                                            (try (json/read-str s :key-fn keyword)
                                                 (catch Exception _ nil))))
      :else nil)))

(deftest begin-with-matches-per-team
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    (sql/insert! ds :organizations {:name "Club"})
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Ana" :email "ana@ex.com" :password "p"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "ana@ex.com" :password "p"})))
          token (:token (decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))]
      (let [pel (app (-> (mock/request :post "/api/peladas") (mock/json-body {:organization_id 1}) auth))]
        (is (= 201 (:status pel))))
      (doseq [n ["A" "B" "C" "D"]]
        (is (= 201 (:status (app (-> (mock/request :post "/api/teams") (mock/json-body {:pelada_id 1 :name n}) auth))))))
      (let [resp (app (-> (mock/request :post "/api/peladas/1/begin")
                          (mock/json-body {:matches_per_team 2})
                          auth))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (pos? (:matches-created body))))
      (let [resp (app (-> (mock/request :get "/api/peladas/1/matches") auth))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (seq body))))))
