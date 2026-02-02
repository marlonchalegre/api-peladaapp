(ns api-peladaapp.match-status-validation-test
  (:require
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
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

(deftest match-update-status-validation-test
  (let [app (-> th/*test-system* :app :handler)]
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

          ;; Create pelada (status: open)
          pelada-resp (app (-> (mock/request :post "/api/peladas")
                               (mock/json-body {:organization_id org-id :num_teams 2})
                               auth))
          pelada-id (:id (decode-body pelada-resp))

          ;; Create teams
          _ (doseq [n ["A" "B"]]
              (app (-> (mock/request :post "/api/teams")
                       (mock/json-body {:pelada_id pelada-id :name n})
                       auth)))

          ;; Close attendance
          _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance")) auth))

          ;; Begin pelada (creates matches, status: running)
          _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin")) auth))

          ;; Get a match id
          matches-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/matches")) auth))
          match-id (-> (decode-body matches-resp) first :id)

          ;; Close pelada (status: closed)
          _ (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close")) auth))]

      ;; Test 1: Try to update score on a closed pelada
      (let [resp (app (-> (mock/request :put (str "/api/matches/" match-id "/score"))
                          (mock/json-body {:home_score 1 :away_score 0 :status "running"})
                          auth))]
        (is (= 400 (:status resp)))
        (is (= "Action only allowed while pelada is running" (:message (decode-body resp)))))

      ;; Test 2: Try to create event on a closed pelada
      (let [resp (app (-> (mock/request :post (str "/api/matches/" match-id "/events"))
                          (mock/json-body {:player_id 1 :event_type "goal"})
                          auth))]
        (is (= 400 (:status resp)))
        (is (= "Action only allowed while pelada is running" (:message (decode-body resp)))))

      ;; Test 3: Try to replace lineup on a closed pelada
      (let [resp (app (-> (mock/request :post (str "/api/matches/" match-id "/lineups/replace"))
                          (mock/json-body {:team_id 1 :out_player_id 1 :in_player_id 2})
                          auth))]
        (is (= 400 (:status resp)))
        (is (= "Action only allowed while pelada is running" (:message (decode-body resp))))))))
