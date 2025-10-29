(ns api-peladaapp.matches-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
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

(deftest matches-flow
  (testing "Complete matches workflow with authorization"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
      
      ;; Register and login
      (app (-> (mock/request :post "/auth/register") 
              (mock/json-body {:name "User" :email "user@test.com" :password "pass"})))
      (let [login (app (-> (mock/request :post "/auth/login") 
                          (mock/json-body {:email "user@test.com" :password "pass"})))
            token (:token (decode-body login))
            auth (fn [req] (mock/header req "authorization" (str "Token " token)))
            user-id (th/user-id-by-email ds "user@test.com")]
        
        ;; Create organization (user becomes admin)
        (let [org-resp (app (-> (mock/request :post "/api/organizations")
                               (mock/json-body {:name "Test Org"})
                               auth))
              org-id (:id (decode-body org-resp))]
          
          ;; Create pelada
          (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                    (mock/json-body {:organization_id org-id})
                                    auth))
                pelada-id (:id (decode-body pelada-resp))]
            
            ;; Create teams
            (doseq [n ["Team A" "Team B"]]
              (app (-> (mock/request :post "/api/teams")
                      (mock/json-body {:pelada_id pelada-id :name n})
                      auth)))
            
            ;; Begin pelada to generate matches
            (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth))]
              (is (= 200 (:status begin-resp))))
            
            ;; List matches (user is member, can view)
            (let [matches-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/matches")) auth))
                  matches (decode-body matches-resp)]
              (is (= 200 (:status matches-resp)))
              (is (seq matches))
              
              ;; Update score of first match (user is admin, can update)
              (when (seq matches)
                (let [match-id (:id (first matches))
                      update-resp (app (-> (mock/request :put (str "/api/matches/" match-id "/score"))
                                          (mock/json-body {:home_score 2 :away_score 1})
                                          auth))]
                  (is (= 200 (:status update-resp))))))))))))
