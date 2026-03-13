(ns api-peladaapp.pelada-close-test
  (:require
   [api-peladaapp.controllers.pelada :as pelada.controller]
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

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

(deftest pelada-close-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; Register and login user
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Test User" :email "test@user.com" :password "password"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "test@user.com" :password "password"})))
          token (:token (decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))

          ;; Create organization
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Test Org"})
                            auth))
          org-id (:id (decode-body org-resp))

          ;; Create pelada
          pelada-resp (app (-> (mock/request :post "/api/peladas")
                               (mock/json-body {:organization_id org-id :num_teams 2})
                               auth))
          pelada-id (:id (decode-body pelada-resp))]

      ;; Create teams
      (doseq [n ["A" "B"]]
        (app (-> (mock/request :post "/api/teams")
                 (mock/json-body {:pelada_id pelada-id :name n})
                 auth)))

      ;; Close attendance
      (is (= 200 (:status (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth)))))

      ;; Begin pelada
      (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
               auth))

      ;; Start pelada and match timers
      (is (= 200 (:status (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/timer/start")) auth)))))

      (let [matches (jdbc/execute! ds ["SELECT id FROM matches WHERE pelada_id = ?" pelada-id])
            match-id (-> matches first :Matches/id)]
        (is (= 200 (:status (app (-> (mock/request :post (str "/api/matches/" match-id "/timer/start")) auth)))))

        ;; Wait a bit more to ensure elapsed time >= 1s for SQLite unixepoch
        (Thread/sleep 1100)

        ;; Close pelada
        (let [close-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close"))
                                  auth))
              body (decode-body close-resp)]
          (is (= 200 (:status close-resp)))
          (is (= "voting" (:status body)))

          ;; Verify pelada timer paused and has elapsed time
          (let [pelada (pelada.controller/get-pelada pelada-id ds)]
            (is (= "paused" (:timer-status pelada)))
            (is (pos? (:timer-accumulated-ms pelada))))

          ;; Verify match timer paused and has elapsed time
          (let [match (jdbc/execute-one! ds ["SELECT * FROM matches WHERE id = ?" match-id])]
            (is (= "finished" (:Matches/status match)))
            (is (= "paused" (:Matches/timer_status match)))
            (is (pos? (:Matches/timer_accumulated_ms match)))))))))
