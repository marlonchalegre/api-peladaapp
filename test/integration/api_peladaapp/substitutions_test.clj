(ns api-peladaapp.substitutions-test
  (:require [clojure.test :refer [deftest is]]
            [ring.mock.request :as mock]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [api-peladaapp.test-helpers :as th]))

(deftest create-and-list-substitutions
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "U" :email "u@e.com" :password "p"})
        auth (th/auth-header token)]
    ;; seed org, pelada, users/players, teams, matches
    (sql/insert! ds :organizations {:name "Org"})
    (sql/insert! ds :peladas {:organization_id 1 :scheduled_at "2025-10-28"})
    (doseq [[name email] [["Ana" "ana@ex.com"] ["Bob" "bob@ex.com"] ["Cid" "cid@ex.com"]]]
      (sql/insert! ds :users {:name name :email email :password "p"}))
    (doseq [uid [1 2 3]]
      (sql/insert! ds :organizationplayers {:organization_id 1 :user_id uid}))
    (doseq [n ["A" "B" "C" "D"]] (sql/insert! ds :teams {:pelada_id 1 :name n}))
    ;; add players 1 and 2 to teams in this pelada
    (sql/insert! ds :teamplayers {:team_id 1 :player_id 1})
    (sql/insert! ds :teamplayers {:team_id 2 :player_id 2})
    (is (= 200 (:status (app (-> (mock/request :post "/api/peladas/1/begin") auth)))))
    ;; create substitution on match 1: out 1, in 2
    (is (= 201 (:status (app (-> (mock/request :post "/api/matches/1/substitutions") (mock/json-body {:out_player_id 1 :in_player_id 2 :minute 5}) auth)))))
    ;; list substitutions for match 1
    (is (= 200 (:status (app (-> (mock/request :get "/api/matches/1/substitutions") auth)))))))
