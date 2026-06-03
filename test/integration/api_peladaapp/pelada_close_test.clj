(ns api-peladaapp.pelada-close-test
  (:require
   [api-peladaapp.controllers.pelada :as pelada.controller]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.notifications :as notifications]
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
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

(defn- exec-one! [ds query]
  (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- exec! [ds query]
  (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest pelada-close-test
  (let [app (-> th/*test-system* :app :app-handler)]
    ;; Register and login user
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Test User" :email "test@user.com" :password "password"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "test@user.com" :password "password"})))
          token (:token (decode-body login))
          auth (th/auth-cookie token)
          db-val (-> th/*test-system* :database :database)
          ds (if (fn? db-val) (db-val) db-val)

          _ (th/grant-org-creation! ds "test@user.com")

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

      (let [matches (exec! ds (-> (h/select :id) (h/from :Matches) (h/where [:= :pelada_id (misc/as-uuid pelada-id)])))
            match-id (:id (first matches))]
        (is (= 200 (:status (app (-> (mock/request :post (str "/api/matches/" match-id "/timer/start")) auth)))))

        ;; Wait a bit more to ensure elapsed time >= 1s for Postgres epoch
        (Thread/sleep 1100)

        ;; Close pelada
        (let [close-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close"))
                                  auth))
              body (decode-body close-resp)]
          (is (= 200 (:status close-resp)))
          (is (= "voting" (:status body)))

          ;; Verify pelada timer paused and has elapsed time
          (let [pelada (pelada.controller/get-pelada (misc/as-uuid pelada-id) ds)]
            (is (= "paused" (:timer-status pelada)))
            (is (pos? (:timer-accumulated-ms pelada))))

          ;; Verify match timer paused and has elapsed time
          (let [match (exec-one! ds (-> (h/select :*) (h/from :Matches) (h/where [:= :id (misc/as-uuid match-id)])))]
            (is (= "finished" (:status match)))
            (is (= "paused" (:timer_status match)))
            (is (pos? (:timer_accumulated_ms match)))))))))

(deftest pelada-close-with-fixed-goalkeeper-notification-test
  (let [app (-> th/*test-system* :app :app-handler)]
    ;; Register and login user
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Test User" :email "test@user.com" :password "password"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "test@user.com" :password "password"})))
          token (:token (decode-body login))
          auth (th/auth-cookie token)
          db-val (-> th/*test-system* :database :database)
          ds (if (fn? db-val) (db-val) db-val)

          _ (th/grant-org-creation! ds "test@user.com")

          ;; Create organization
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Test Org"})
                            auth))
          org-id (:id (decode-body org-resp))

          ;; Create pelada
          pelada-resp (app (-> (mock/request :post "/api/peladas")
                               (mock/json-body {:organization_id org-id :num_teams 2})
                               auth))
          pelada-id (:id (decode-body pelada-resp))

          ;; Create fixed goalkeeper user and player
          gk-user-id (parse-uuid "00000000-0000-0000-0000-000000000100")
          gk-player-id (parse-uuid "00000000-0000-0000-0000-000000000200")]

      ;; Insert fixed goalkeeper records
      (jdbc/execute! ds (hsql/format (-> (h/insert-into :Users)
                                         (h/values [{:id gk-user-id
                                                     :email "fixedgk@test.com"
                                                     :name "My Fixed Goalkeeper Name"
                                                     :username "myfixedgk"}]))))
      (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationPlayers)
                                         (h/values [{:id gk-player-id
                                                     :organization_id (misc/as-uuid org-id)
                                                     :user_id gk-user-id
                                                     :member_type [[:cast "diarista" :member_type]]}]))))

      ;; Set as fixed goalkeeper for the pelada
      (db.pelada/update-pelada (misc/as-uuid pelada-id)
                               {:home-fixed-goalkeeper-id gk-player-id}
                               ds)

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

      (let [matches (exec! ds (-> (h/select :id) (h/from :Matches) (h/where [:= :pelada_id (misc/as-uuid pelada-id)])))
            match-id (:id (first matches))]
        (is (= 200 (:status (app (-> (mock/request :post (str "/api/matches/" match-id "/timer/start")) auth)))))

        (Thread/sleep 1100)

        ;; Close pelada and intercept notification sending
        (let [sent-data (atom nil)
              latch (java.util.concurrent.CountDownLatch. 1)]
          (with-redefs [notifications/send-notification! (fn [_ type data _]
                                                           (reset! sent-data {:type type :data data})
                                                           (.countDown latch))]
            (let [close-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close"))
                                      auth))]
              (is (= 200 (:status close-resp)))
              ;; Wait for the future to execute and trigger notification
              (.await latch 5 java.util.concurrent.TimeUnit/SECONDS)
              (is (some? @sent-data))
              (is (= :end (:type @sent-data)))
              (let [team-players (get-in @sent-data [:data :team-players])
                    resolved-gk (first (filter #(= gk-player-id (:player_id %)) team-players))]
                (is (some? resolved-gk))
                (is (= "My Fixed Goalkeeper Name" (:player_name resolved-gk)))))))))))
