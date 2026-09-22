(ns integration.api-peladaapp.organization-weekly-presence-test
  (:require
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- exec-one! [ds query]
  (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- seed-confirmed-attendance! [ds org-id scheduled-at]
  (let [pelada-id (:id (exec-one! ds (-> (h/insert-into :Peladas)
                                         (h/values [{:organization_id org-id
                                                     :scheduled_at [[:cast scheduled-at :timestamp]]
                                                     :status [:cast "closed" :pelada_status]}])
                                         (h/returning :id))))
        player-id (:id (exec-one! ds (-> (h/select :id) (h/from :OrganizationPlayers) (h/where [:= :organization_id org-id]))))]
    (exec! ds (-> (h/insert-into :Attendance)
                  (h/values [{:pelada_id pelada-id
                              :player_id player-id
                              :status [:cast "confirmed" :attendance_status]}])))))

(deftest organization-weekly-presence-test
  (let [app (-> th/*test-system* :app :app-handler)
        db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        token (th/register-and-login! app {:name "Presence Member" :email "presence_member@test.com" :password "pass123"})
        org-id (parse-uuid (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                                         (mock/json-body {:name "Org Presence"})
                                                         (th/auth-cookie token))))))
        _ (seed-confirmed-attendance! ds org-id "2026-08-31 19:00:00")
        _ (seed-confirmed-attendance! ds org-id "2026-09-09 19:00:00")
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/weekly-presence"))
                          ((th/auth-cookie token))))
        body (th/decode-body response)]
    (testing "returns confirmed counts per week, oldest first, defaulting to 12 weeks"
      (is (= 200 (:status response)))
      (is (= [{:week_start "2026-08-31" :confirmed 1}
              {:week_start "2026-09-07" :confirmed 1}]
             body)))
    (testing "weeks parameter limits how many recent weeks are returned"
      (let [limited (th/decode-body (app (-> (mock/request :get (str "/api/organizations/" org-id "/weekly-presence"))
                                             (mock/query-string {:weeks 1})
                                             ((th/auth-cookie token)))))]
        (is (= [{:week_start "2026-09-07" :confirmed 1}] limited))))))

(deftest organization-weekly-presence-empty-test
  (let [app (-> th/*test-system* :app :app-handler)
        token (th/register-and-login! app {:name "Presence Empty" :email "presence_empty@test.com" :password "pass123"})
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Org Presence Empty"})
                                             (th/auth-cookie token)))))
        response (app (-> (mock/request :get (str "/api/organizations/" org-id "/weekly-presence"))
                          ((th/auth-cookie token))))]
    (is (= 200 (:status response)))
    (is (= [] (th/decode-body response)))))

(deftest organization-weekly-presence-access-and-validation-test
  (let [app (-> th/*test-system* :app :app-handler)
        member-token (th/register-and-login! app {:name "Presence Member" :email "presence_member2@test.com" :password "pass123"})
        outsider-token (th/register-and-login! app {:name "Presence Outsider" :email "presence_outsider@test.com" :password "pass123"})
        org-id (:id (th/decode-body (app (-> (mock/request :post "/api/organizations")
                                             (mock/json-body {:name "Org Presence Access"})
                                             (th/auth-cookie member-token)))))]

    (testing "non-members get 403"
      (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/weekly-presence"))
                              ((th/auth-cookie outsider-token))))]
        (is (= 403 (:status response)))))

    (testing "unauthenticated requests get 401"
      (let [response (app (mock/request :get (str "/api/organizations/" org-id "/weekly-presence")))]
        (is (= 401 (:status response)))))

    (testing "invalid weeks parameter gets 400"
      (let [response (app (-> (mock/request :get (str "/api/organizations/" org-id "/weekly-presence"))
                              (mock/query-string {:weeks "soon"})
                              ((th/auth-cookie member-token))))]
        (is (= 400 (:status response)))))))
