(ns api-100folego.match-events-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [api-100folego.test-helpers :as th]))

(deftest player-stats-endpoint-returns-unqualified-columns
  (let [{:keys [app db-file]} (th/make-app!)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})
        email "stats@example.com"
        token (th/register-and-login! app {:name "Stats User" :email email :password "secret"})
        auth (th/auth-header token)
        user-id (th/user-id-by-email ds email)]
    (sql/insert! ds :organizations {:id 1 :name "Org"})
    (sql/insert! ds :organizationplayers {:id 13 :organization_id 1 :user_id user-id})
    (sql/insert! ds :peladas {:id 1 :organization_id 1 :scheduled_at "2025-10-28"})
    (sql/insert! ds :teams {:id 1 :pelada_id 1 :name "Home"})
    (sql/insert! ds :teams {:id 2 :pelada_id 1 :name "Away"})
    (sql/insert! ds :matches {:id 1 :pelada_id 1 :home_team_id 1 :away_team_id 2 :sequence 1 :status "finished" :home_score 1 :away_score 0})
    (sql/insert! ds :matchevents {:match_id 1 :player_id 13 :event_type "goal"})
    (let [resp (app (-> (mock/request :get "/api/peladas/1/player-stats") auth))
          body (th/decode-body resp)]
      (is (= 200 (:status resp)))
      (is (= [{:player_id 13 :goals 1 :assists 0 :own_goals 0}] body)))))
