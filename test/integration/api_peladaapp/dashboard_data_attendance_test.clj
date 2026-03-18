(ns api-peladaapp.dashboard-data-attendance-test
  (:require
   [api-peladaapp.server :as server]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(deftest get-dashboard-data-attendance-test
  (let [db-raw (-> th/*test-system* :database :database)
        db (if (fn? db-raw) (db-raw) db-raw)
        app (fn [req] (server/app (assoc req :database db)))

        token (th/register-and-login! app {:name "Admin" :email "admin@test.com" :password "pass"})
        auth (fn [req] (mock/header req "Authorization" (str "Token " token)))

        ;; Setup: Org -> Pelada
        org-resp (app (-> (mock/request :post "/api/organizations") (mock/json-body {:name "Org"}) auth))
        org-id (:id (th/decode-body org-resp))
        pelada-resp (app (-> (mock/request :post "/api/peladas") (mock/json-body {:organization_id org-id}) auth))
        pelada-id (:id (th/decode-body pelada-resp))

        ;; Get player-id (admin is added as player by default)
        players-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/players")) auth))
        player-id (:id (first (th/decode-body players-resp)))]

    (testing "Attendance is returned in dashboard-data"
      ;; Make mensalista
      (app (-> (mock/request :put (str "/api/players/" player-id))
               (mock/json-body {:member_type "mensalista"})
               auth))

      ;; Confirm attendance first
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
               (mock/json-body {:status "confirmed"})
               auth))

      (let [resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/dashboard-data")) auth))
            body (th/decode-body resp)]
        (is (= 200 (:status resp)))
        (is (contains? body :attendance))
        (let [att (first (:attendance body))]
          (is (= player-id (:player_id att)))
          (is (= "confirmed" (:status att))))))))
