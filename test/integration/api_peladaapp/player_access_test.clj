(ns api-peladaapp.player-access-test
  (:require [clojure.test :refer [deftest is testing]]
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

(deftest player-can-view-organization-data
  (testing "A player belonging to an organization can view organization data"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
      
      ;; Register admin user
      (app (-> (mock/request :post "/auth/register") 
              (mock/json-body {:name "Admin User" :email "admin@test.com" :password "admin123"})))
      (let [admin-login (app (-> (mock/request :post "/auth/login") 
                                (mock/json-body {:email "admin@test.com" :password "admin123"})))
            admin-token (:token (decode-body admin-login))
            admin-auth (fn [req] (mock/header req "authorization" (str "Token " admin-token)))
            admin-user-id (th/user-id-by-email ds "admin@test.com")]
        
        ;; Register player user
        (app (-> (mock/request :post "/auth/register") 
                (mock/json-body {:name "Player User" :email "player@test.com" :password "player123"})))
        (let [player-login (app (-> (mock/request :post "/auth/login") 
                                   (mock/json-body {:email "player@test.com" :password "player123"})))
              player-token (:token (decode-body player-login))
              player-auth (fn [req] (mock/header req "authorization" (str "Token " player-token)))
              player-user-id (th/user-id-by-email ds "player@test.com")]
          
          ;; Admin creates organization
          (let [org-resp (app (-> (mock/request :post "/api/organizations")
                                 (mock/json-body {:name "Test Organization"})
                                 admin-auth))
                org-id (:id (decode-body org-resp))]
            (is (= 201 (:status org-resp)))
            
            ;; Admin adds player to organization
            (let [add-player-resp (app (-> (mock/request :post "/api/players")
                                          (mock/json-body {:organization_id org-id 
                                                          :user_id player-user-id
                                                          :grade 7.5
                                                          :position_id 1})
                                          admin-auth))]
              (is (= 201 (:status add-player-resp))))
            
            ;; Player can view organization details
            (let [get-org-resp (app (-> (mock/request :get (str "/api/organizations/" org-id))
                                       player-auth))
                  org-data (decode-body get-org-resp)]
              (is (= 200 (:status get-org-resp)))
              (is (= org-id (:id org-data)))
              (is (= "Test Organization" (:name org-data))))
            
            ;; Player can list organizations
            (let [list-org-resp (app (-> (mock/request :get "/api/organizations")
                                        player-auth))
                  orgs (decode-body list-org-resp)]
              (is (= 200 (:status list-org-resp)))
              (is (seq orgs)))
            
            ;; Player can view players in the organization
            (let [list-players-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/players"))
                                            player-auth))
                  players (decode-body list-players-resp)]
              (is (= 200 (:status list-players-resp)))
              (is (= 1 (count players)))
              (is (= player-user-id (:user_id (first players)))))))))))

(deftest player-can-view-peladas
  (testing "A player belonging to an organization can view peladas of that organization"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
      
      ;; Register admin user
      (app (-> (mock/request :post "/auth/register") 
              (mock/json-body {:name "Admin User" :email "admin@test.com" :password "admin123"})))
      (let [admin-login (app (-> (mock/request :post "/auth/login") 
                                (mock/json-body {:email "admin@test.com" :password "admin123"})))
            admin-token (:token (decode-body admin-login))
            admin-auth (fn [req] (mock/header req "authorization" (str "Token " admin-token)))
            admin-user-id (th/user-id-by-email ds "admin@test.com")]
        
        ;; Register player user
        (app (-> (mock/request :post "/auth/register") 
                (mock/json-body {:name "Player User" :email "player@test.com" :password "player123"})))
        (let [player-login (app (-> (mock/request :post "/auth/login") 
                                   (mock/json-body {:email "player@test.com" :password "player123"})))
              player-token (:token (decode-body player-login))
              player-auth (fn [req] (mock/header req "authorization" (str "Token " player-token)))
              player-user-id (th/user-id-by-email ds "player@test.com")]
          
          ;; Admin creates organization
          (let [org-resp (app (-> (mock/request :post "/api/organizations")
                                 (mock/json-body {:name "Test Organization"})
                                 admin-auth))
                org-id (:id (decode-body org-resp))]
            
            ;; Admin adds player to organization
            (app (-> (mock/request :post "/api/players")
                    (mock/json-body {:organization_id org-id 
                                    :user_id player-user-id
                                    :grade 7.5
                                    :position_id 1})
                    admin-auth))
            
            ;; Admin creates pelada
            (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                      (mock/json-body {:organization_id org-id
                                                      :num_teams 4})
                                      admin-auth))
                  pelada-id (:id (decode-body pelada-resp))]
              (is (= 201 (:status pelada-resp)))
              
              ;; Player can view pelada by ID
              (let [get-pelada-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id))
                                            player-auth))
                    pelada-data (decode-body get-pelada-resp)]
                (is (= 200 (:status get-pelada-resp)))
                (is (= pelada-id (:id pelada-data)))
                (is (= org-id (:organization_id pelada-data))))
              
              ;; Player can list peladas by organization
              (let [list-peladas-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/peladas"))
                                              player-auth))
                    peladas (decode-body list-peladas-resp)]
                (is (= 200 (:status list-peladas-resp)))
                (is (= 1 (count peladas)))
                (is (= pelada-id (:id (first peladas))))))))))))

(deftest player-can-view-matches
  (testing "A player belonging to an organization can view matches of peladas"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
      
      ;; Register admin user
      (app (-> (mock/request :post "/auth/register") 
              (mock/json-body {:name "Admin User" :email "admin@test.com" :password "admin123"})))
      (let [admin-login (app (-> (mock/request :post "/auth/login") 
                                (mock/json-body {:email "admin@test.com" :password "admin123"})))
            admin-token (:token (decode-body admin-login))
            admin-auth (fn [req] (mock/header req "authorization" (str "Token " admin-token)))
            admin-user-id (th/user-id-by-email ds "admin@test.com")]
        
        ;; Register player user
        (app (-> (mock/request :post "/auth/register") 
                (mock/json-body {:name "Player User" :email "player@test.com" :password "player123"})))
        (let [player-login (app (-> (mock/request :post "/auth/login") 
                                   (mock/json-body {:email "player@test.com" :password "player123"})))
              player-token (:token (decode-body player-login))
              player-auth (fn [req] (mock/header req "authorization" (str "Token " player-token)))
              player-user-id (th/user-id-by-email ds "player@test.com")]
          
          ;; Admin creates organization
          (let [org-resp (app (-> (mock/request :post "/api/organizations")
                                 (mock/json-body {:name "Test Organization"})
                                 admin-auth))
                org-id (:id (decode-body org-resp))]
            
            ;; Admin adds player to organization
            (app (-> (mock/request :post "/api/players")
                    (mock/json-body {:organization_id org-id 
                                    :user_id player-user-id
                                    :grade 7.5
                                    :position_id 1})
                    admin-auth))
            
            ;; Admin creates pelada
            (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                      (mock/json-body {:organization_id org-id
                                                      :num_teams 4})
                                      admin-auth))
                  pelada-id (:id (decode-body pelada-resp))]
              
              ;; Admin begins pelada to generate matches
              (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                                       admin-auth))]
                (is (= 200 (:status begin-resp)))
                (is (pos? (:matches-created (decode-body begin-resp)))))
              
              ;; Player can list matches
              (let [list-matches-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/matches"))
                                              player-auth))
                    matches (decode-body list-matches-resp)]
                (is (= 200 (:status list-matches-resp)))
                (is (seq matches))
                (is (every? #(= pelada-id (:pelada_id %)) matches)))
              
              ;; Player can view match lineups
              (let [matches (decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/matches"))
                                                 player-auth)))
                    first-match-id (:id (first matches))]
                (when first-match-id
                  (let [lineups-resp (app (-> (mock/request :get (str "/api/matches/" first-match-id "/lineups"))
                                             player-auth))
                        lineups (decode-body lineups-resp)]
                    (is (= 200 (:status lineups-resp)))
                    (is (map? lineups)))))
              
              ;; Player can view match events
              (let [events-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/events"))
                                        player-auth))
                    events (decode-body events-resp)]
                (is (= 200 (:status events-resp)))
                ;; Events list may be empty if no events were created
                (is (or (nil? events) (vector? events))))
              
              ;; Player can view player stats
              (let [stats-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/player-stats"))
                                       player-auth))
                    stats (decode-body stats-resp)]
                (is (= 200 (:status stats-resp)))
                ;; Stats list may be empty if no events were created
                (is (or (nil? stats) (vector? stats)))))))))))

(deftest player-cannot-modify-peladas-or-matches
  (testing "A player (non-admin) cannot modify peladas or matches"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
      
      ;; Register admin user
      (app (-> (mock/request :post "/auth/register") 
              (mock/json-body {:name "Admin User" :email "admin@test.com" :password "admin123"})))
      (let [admin-login (app (-> (mock/request :post "/auth/login") 
                                (mock/json-body {:email "admin@test.com" :password "admin123"})))
            admin-token (:token (decode-body admin-login))
            admin-auth (fn [req] (mock/header req "authorization" (str "Token " admin-token)))
            admin-user-id (th/user-id-by-email ds "admin@test.com")]
        
        ;; Register player user
        (app (-> (mock/request :post "/auth/register") 
                (mock/json-body {:name "Player User" :email "player@test.com" :password "player123"})))
        (let [player-login (app (-> (mock/request :post "/auth/login") 
                                   (mock/json-body {:email "player@test.com" :password "player123"})))
              player-token (:token (decode-body player-login))
              player-auth (fn [req] (mock/header req "authorization" (str "Token " player-token)))
              player-user-id (th/user-id-by-email ds "player@test.com")]
          
          ;; Admin creates organization
          (let [org-resp (app (-> (mock/request :post "/api/organizations")
                                 (mock/json-body {:name "Test Organization"})
                                 admin-auth))
                org-id (:id (decode-body org-resp))]
            
            ;; Admin adds player to organization
            (app (-> (mock/request :post "/api/players")
                    (mock/json-body {:organization_id org-id 
                                    :user_id player-user-id
                                    :grade 7.5
                                    :position_id 1})
                    admin-auth))
            
            ;; Player cannot create pelada
            (let [create-pelada-resp (app (-> (mock/request :post "/api/peladas")
                                             (mock/json-body {:organization_id org-id
                                                             :num_teams 4})
                                             player-auth))]
              (is (or (= 403 (:status create-pelada-resp))
                     (= 401 (:status create-pelada-resp)))))
            
            ;; Admin creates pelada
            (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                      (mock/json-body {:organization_id org-id
                                                      :num_teams 4})
                                      admin-auth))
                  pelada-id (:id (decode-body pelada-resp))]
              
              ;; Player cannot update pelada
              (let [update-resp (app (-> (mock/request :put (str "/api/peladas/" pelada-id))
                                        (mock/json-body {:status "running"})
                                        player-auth))]
                (is (or (= 403 (:status update-resp))
                       (= 401 (:status update-resp)))))
              
              ;; Player cannot begin pelada
              (let [begin-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                                       player-auth))]
                (is (or (= 403 (:status begin-resp))
                       (= 401 (:status begin-resp)))))
              
              ;; Admin begins pelada
              (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                      admin-auth))
              
              ;; Get first match
              (let [matches (decode-body (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/matches"))
                                                 admin-auth)))
                    first-match-id (:id (first matches))]
                
                ;; Player cannot update match score
                (when first-match-id
                  (let [score-resp (app (-> (mock/request :put (str "/api/matches/" first-match-id "/score"))
                                           (mock/json-body {:home_score 2 :away_score 1})
                                           player-auth))]
                    (is (or (= 403 (:status score-resp))
                           (= 401 (:status score-resp))))))
                
                ;; Player cannot create match events
                (when first-match-id
                  (let [event-resp (app (-> (mock/request :post (str "/api/matches/" first-match-id "/events"))
                                           (mock/json-body {:player_id player-user-id :event_type "goal"})
                                           player-auth))]
                    (is (or (= 403 (:status event-resp))
                           (= 401 (:status event-resp))))))
                
                ;; Player cannot delete pelada
                (let [delete-resp (app (-> (mock/request :delete (str "/api/peladas/" pelada-id))
                                          player-auth))]
                  (is (or (= 403 (:status delete-resp))
                         (= 401 (:status delete-resp)))))))))))))

(deftest non-member-cannot-view-organization-data
  (testing "A user who is not a member of an organization cannot view its data"
    (let [{:keys [app db-file]} (th/make-app!)
          ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
      
      ;; Register admin user
      (app (-> (mock/request :post "/auth/register") 
              (mock/json-body {:name "Admin User" :email "admin@test.com" :password "admin123"})))
      (let [admin-login (app (-> (mock/request :post "/auth/login") 
                                (mock/json-body {:email "admin@test.com" :password "admin123"})))
            admin-token (:token (decode-body admin-login))
            admin-auth (fn [req] (mock/header req "authorization" (str "Token " admin-token)))]
        
        ;; Register non-member user
        (app (-> (mock/request :post "/auth/register") 
                (mock/json-body {:name "Outsider User" :email "outsider@test.com" :password "outsider123"})))
        (let [outsider-login (app (-> (mock/request :post "/auth/login") 
                                     (mock/json-body {:email "outsider@test.com" :password "outsider123"})))
              outsider-token (:token (decode-body outsider-login))
              outsider-auth (fn [req] (mock/header req "authorization" (str "Token " outsider-token)))]
          
          ;; Admin creates organization
          (let [org-resp (app (-> (mock/request :post "/api/organizations")
                                 (mock/json-body {:name "Test Organization"})
                                 admin-auth))
                org-id (:id (decode-body org-resp))]
            
            ;; Admin creates pelada
            (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                      (mock/json-body {:organization_id org-id
                                                      :num_teams 4})
                                      admin-auth))
                  pelada-id (:id (decode-body pelada-resp))]
              
              ;; Admin begins pelada
              (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                      admin-auth))
              
              ;; Non-member cannot view pelada
              (let [get-pelada-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id))
                                            outsider-auth))]
                (is (or (= 403 (:status get-pelada-resp))
                       (= 401 (:status get-pelada-resp)))))
              
              ;; Non-member cannot list peladas
              (let [list-peladas-resp (app (-> (mock/request :get (str "/api/organizations/" org-id "/peladas"))
                                              outsider-auth))]
                (is (or (= 403 (:status list-peladas-resp))
                       (= 401 (:status list-peladas-resp)))))
              
              ;; Non-member cannot view matches
              (let [list-matches-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/matches"))
                                              outsider-auth))]
                (is (or (= 403 (:status list-matches-resp))
                       (= 401 (:status list-matches-resp))))))))))))
