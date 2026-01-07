(ns api-peladaapp.pelada-close-test
  (:require
   [api-peladaapp.controllers.pelada :as pelada.controller]
   [api-peladaapp.test-helpers :as th]
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is use-fixtures]]
   [next.jdbc :as jdbc]
   [ring.mock.request :as mock]))

(use-fixtures :each th/test-system-fixture)

(defn- decode-body [resp]
  (let [b (:body resp)]
    (cond
      (map? b) b
      (string? b) (when-not (clojure.string/blank? b)
                    (try (json/read-str b :key-fn keyword)
                         (catch Exception _ nil)))
      (instance? java.io.InputStream b) (let [s (slurp b)]
                                          (when-not (clojure.string/blank? s)
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
          _user-id (th/user-id-by-email ds "test@user.com")]

      ;; Create organization
      (let [org-resp (app (-> (mock/request :post "/api/organizations")
                              (mock/json-body {:name "Test Org"})
                              auth))
            org-id (:id (decode-body org-resp))]

        ;; Create pelada
        (let [pelada-resp (app (-> (mock/request :post "/api/peladas")
                                   (mock/json-body {:organization_id org-id :num_teams 2})
                                   auth))
              pelada-id (:id (decode-body pelada-resp))]

          ;; Begin pelada
          (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/begin"))
                   auth))

          ;; Close pelada
          (let [close-resp (app (-> (mock/request :post (str "/api/peladas/" pelada-id "/close"))
                                    auth))
                body (decode-body close-resp)]
            (is (= 200 (:status close-resp)))
            (is (= "closed" (:status body)))
            (is (not (nil? (:closed_at body))))

            (let [pelada (pelada.controller/get-pelada pelada-id ds)]
              (is (= "closed" (:status pelada)))
              (is (not (nil? (:closed_at pelada)))))))))))
