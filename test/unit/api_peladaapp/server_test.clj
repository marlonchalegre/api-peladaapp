(ns api-peladaapp.server-test
  (:require
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.server :as server]
   [clojure.test :refer [deftest is testing]]))

(deftest test-on-error
  (testing "handles authentication type"
    (let [res (server/on-error {} {:type :authentication :message "Custom auth message"})]
      (is (= 401 (:status res)))
      (is (= "Custom auth message" (get-in res [:body :error])))
      (is (= "authentication" (get-in res [:body :type]))))
    (let [res (server/on-error {} {:type :authentication})]
      (is (= 401 (:status res)))
      (is (= "Authentication required" (get-in res [:body :error])))))

  (testing "handles unauthorized type"
    (let [res (server/on-error {} {:type :unauthorized})]
      (is (= 401 (:status res)))
      (is (= "Authentication required" (get-in res [:body :error])))))

  (testing "handles forbidden type"
    (let [res (server/on-error {} {:type :forbidden})]
      (is (= 403 (:status res)))
      (is (= "Access forbidden" (get-in res [:body :error])))))

  (testing "handles default/other types"
    (let [res (server/on-error {} {:type :other-error})]
      (is (= 403 (:status res)))
      (is (= "Access denied" (get-in res [:body :error]))))))

(deftest test-wrap-assoc
  (testing "only sets key if not already present"
    (let [value-delay (delay "new-val")
          handler (fn [req] req)
          middleware (server/wrap-assoc handler :key value-delay)]
      (is (= "existing-val" (:key (middleware {:key "existing-val"}))))
      (is (= "new-val" (:key (middleware {})))))))

(deftest test-wrap-exception-log
  (testing "passes through normal response"
    (let [handler (fn [_] {:status 200})
          middleware (server/wrap-exception-log handler)]
      (is (= 200 (:status (middleware {}))))))

  (testing "handles 500 response"
    (let [handler (fn [_] {:status 500 :body "error"})
          middleware (server/wrap-exception-log handler)]
      (is (= 500 (:status (middleware {}))))))

  (testing "logs and rethrows exceptions"
    (let [handler (fn [_] (throw (Exception. "fatal")))
          middleware (server/wrap-exception-log handler)]
      (is (thrown-with-msg? Exception #"fatal" (middleware {}))))))

(deftest test-wrap-blocked-user-check
  (let [uuid-1 (random-uuid)
        active-user {:id uuid-1 :is-blocked false}
        blocked-user {:id uuid-1 :is-blocked true}
        dummy-handler (fn [req] {:status 200 :body (:identity req)})
        middleware (server/wrap-blocked-user-check dummy-handler)]
    (testing "when identity or database is missing, calls handler"
      (is (= 200 (:status (middleware {}))))
      (is (= 200 (:status (middleware {:identity {:id uuid-1}})))))

    (testing "when user is active, calls handler"
      (with-redefs [db.user/find-user-by-id (fn [_ _] active-user)]
        (let [resp (middleware {:identity {:id (str uuid-1)} :database "db"})]
          (is (= 200 (:status resp))))))

    (testing "when user is blocked"
      (with-redefs [db.user/find-user-by-id (fn [_ _] blocked-user)]
        (testing "allows access to profile/avatar/auth/health/internal endpoints"
          (doseq [uri ["/api/users/me"
                       (str "/api/user/" uuid-1)
                       (str "/api/user/" uuid-1 "/profile")
                       (str "/api/user/" uuid-1 "/avatar")
                       "/auth/login"
                       "/auth/logout"
                       "/api/health"
                       "/internal/metrics"]]
            (let [resp (middleware {:identity {:id uuid-1} :database "db" :uri uri})]
              (is (= 200 (:status resp)) (str "Should allow " uri)))))

        (testing "denies access to other endpoints with 403"
          (let [resp (middleware {:identity {:id uuid-1} :database "db" :uri "/api/peladas"})]
            (is (= 403 (:status resp)))
            (is (= "forbidden" (get-in resp [:body :type])))))))))
