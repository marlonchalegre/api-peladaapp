(ns api-100folego.teams-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [api-100folego.test-helpers :as th]))

(deftest team-crud-and-players
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "U" :email "u@e.com" :password "p"})
        auth (th/auth-header token)]
    ;; seed org, pelada, users and players
    (sql/insert! ds :organizations {:name "Org"})
    (sql/insert! ds :peladas {:organization_id 1 :scheduled_at "2025-10-28"})
    (doseq [[name email] [["Ana" "ana@example.com"] ["Bob" "bob@example.com"]]]
      (sql/insert! ds :users {:name name :email email :password "p"}))
    (doseq [uid [1 2]]
      (sql/insert! ds :organizationplayers {:organization_id 1 :user_id uid}))
    ;; create team
    (let [resp (app (-> (mock/request :post "/api/teams")
                        (mock/json-body {:pelada_id 1 :name "A"})
                        auth))
          body (th/decode-body resp)]
      (is (= 201 (:status resp))))
    ;; add players
    (is (= 201 (:status (app (-> (mock/request :post "/api/teams/1/players") (mock/json-body {:player_id 1}) auth)))))
    (is (= 201 (:status (app (-> (mock/request :post "/api/teams/1/players") (mock/json-body {:player_id 2}) auth)))))
    ;; list teams
    (let [resp (app (-> (mock/request :get "/api/peladas/1/teams") auth))
          body (th/decode-body resp)]
      (is (= 200 (:status resp)))
      (is (= 1 (count body))))
    ;; remove player
    (let [resp (app (-> (mock/request :delete "/api/teams/1/players") (mock/json-body {:player_id 1}) auth))]
      (is (= 200 (:status resp)))
      (is (= {} (th/decode-body resp))))))
