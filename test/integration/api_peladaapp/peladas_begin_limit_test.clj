(ns api-peladaapp.peladas-begin-limit-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
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

(deftest begin-with-matches-per-team
  (testing "Begin pelada with matches_per_team parameter"
    (let [app (-> th/*test-system* :app :handler)
          db-file (:db-file th/*test-system*)]

      ;; Register and login
      (app (-> (mock/request :post "/auth/register")
               (mock/json-body {:name "User" :email "user@test.com" :password "pass"})))
      (let [login (app (-> (mock/request :post "/auth/login")
                           (mock/json-body {:email "user@test.com" :password "pass"})))
            token (:token (decode-body login))
            auth (fn [req] (mock/header req "authorization" (str "Token " token)))

            ;; Create organization (user becomes admin)
            org-resp (app (-> (mock/request :post "/api/organizations")
                                (mock/json-body {:name "Test Org"})
                                auth))
            org-id (:id (decode-body org-resp))

            ;; Create pelada
            pelada-resp (app (-> (mock/request :post "/api/peladas")
                                     (mock/json-body {:organization_id org-id})
                                     auth))
            pelada-id (:id (decode-body pelada-resp))]

            ;; Create 4 teams
            (doseq [n ["A" "B" "C" "D"]]
              (app (-> (mock/request :post "/api/teams")
                       (mock/json-body {:pelada_id pelada-id :name n})
                       auth)))

            ;; Close attendance
            (is (= 200 (:status (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth)))))

            ;; Begin pelada with matches_per_team = 2
            (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                                      (mock/json-body {:matches_per_team 2})
                                      auth))
                  begin-body (decode-body begin-resp)]
              (is (= 200 (:status begin-resp)))
              (is (pos? (:matches_created begin-body))) ;; Matches were created

              ;; Verify matches were created
              (let [matches-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/matches")) auth))
                    matches (decode-body matches-resp)]
                (is (= 200 (:status matches-resp)))
                (is (= (:matches_created begin-body) (count matches)))))))))
