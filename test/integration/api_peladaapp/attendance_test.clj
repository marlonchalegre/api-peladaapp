(ns api-peladaapp.attendance-test
  (:require
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

(deftest attendance-flow-test
  (let [app (-> th/*test-system* :app :handler)
        db-file (:db-file th/*test-system*)
        ds (jdbc/get-datasource {:dbtype "sqlite" :dbname db-file})]
    ;; Register and login user
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Player 1" :email "p1@ex.com" :password "p"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "p1@ex.com" :password "p"})))
          token (:token (decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))
          user-id (th/user-id-by-email ds "p1@ex.com")

          ;; Create organization (user becomes admin automatically)
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Attendance Club"})
                            auth))
          org-id (:id (decode-body org-resp))]
      (is (= 201 (:status org-resp)))

        ;; Create player for user
      (let [player-resp (app (-> (mock/request :post "/api/players")
                                 (mock/json-body {:organization_id org-id :user_id user-id :grade 5})
                                 auth))]
        (is (= 201 (:status player-resp))))

        ;; Create pelada
      (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                 (mock/json-body {:organization_id org-id})
                                 auth))
            pelada-id (:id (decode-body pelada-resp))]
        (is (= 201 (:status pelada-resp)))

          ;; Update attendance (this was causing 500 error)
        (let [att-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/attendance"))
                                (mock/json-body {:status "confirmed"})
                                auth))
              att-body (decode-body att-resp)]
          (is (= 200 (:status att-resp)))
          (is (= 1 (:result att-body)))
          (is (not (instance? java.lang.Integer (:body att-resp))))

            ;; Verify database state
          (let [check-resp (app (-> (mock/request :get (str "/api/peladas/" pelada-id "/full-details"))
                                    auth))
                pelada-data (decode-body check-resp)
                attendance (:attendance pelada-data)
                available (:available_players pelada-data)]
              ;; Security check: password must not be returned
            (is (not (contains? (get-in available [0 :user]) :password)))
            (is (not (contains? (get-in attendance [0 :player :user]) :password)))

              ;; Check attendance list
            (is (some (fn [a] (and (= (:status a) "confirmed")
                                   (= (get-in a [:player :user_id]) user-id)))
                      attendance))
              ;; Check available_players list (which is what the frontend uses)
            (is (some (fn [p] (and (= (:attendance_status p) "confirmed")
                                   (= (:user_id p) user-id)))
                      available))))

          ;; Close attendance
        (let [close-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close-attendance"))
                                  auth))]
          (is (= 200 (:status close-resp))))))))
