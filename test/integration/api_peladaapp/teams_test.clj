(ns api-peladaapp.teams-test
  (:require [clojure.test :refer [deftest is]]
            [ring.mock.request :as mock]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [api-peladaapp.test-helpers :as th]))

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
                        auth))]
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

(deftest cannot-add-player-from-different-organization
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        token (th/register-and-login! app {:name "U" :email "u@e.com" :password "p"})
        auth (th/auth-header token)]
    ;; Create two organizations
    (sql/insert! ds :organizations {:name "Org1"})
    (sql/insert! ds :organizations {:name "Org2"})
    ;; Create pelada for Org1
    (sql/insert! ds :peladas {:organization_id 1 :scheduled_at "2025-10-28"})
    ;; Create users
    (sql/insert! ds :users {:name "Ana" :email "ana@example.com" :password "p"})
    (sql/insert! ds :users {:name "Bob" :email "bob@example.com" :password "p"})
    ;; Ana is in Org1, Bob is in Org2
    (sql/insert! ds :organizationplayers {:organization_id 1 :user_id 2})
    (sql/insert! ds :organizationplayers {:organization_id 2 :user_id 3})
    ;; Create team for pelada (which is in Org1)
    (let [resp (app (-> (mock/request :post "/api/teams")
                        (mock/json-body {:pelada_id 1 :name "Team A"})
                        auth))]
      (is (= 201 (:status resp))))
    ;; Try to add Ana (player_id 1, from Org1) - should succeed
    (let [resp (app (-> (mock/request :post "/api/teams/1/players")
                        (mock/json-body {:player_id 1})
                        auth))]
      (is (= 201 (:status resp))))
    ;; Try to add Bob (player_id 2, from Org2) - should fail
    (let [resp (app (-> (mock/request :post "/api/teams/1/players")
                        (mock/json-body {:player_id 2})
                        auth))]
      (is (= 400 (:status resp)))
      (let [body (th/decode-body resp)]
        (is (contains? body :message))
        (is (= "Player does not belong to the pelada's organization" (:message body)))))))
