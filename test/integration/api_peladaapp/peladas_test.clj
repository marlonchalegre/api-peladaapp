(ns api-peladaapp.peladas-test
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

(deftest pelada-crud-and-begin
  (let [app (-> th/*test-system* :app :handler)]
    ;; Register and login user
    (app (-> (mock/request :post "/auth/register") (mock/json-body {:name "Ana" :email "ana@ex.com" :password "p"})))
    (let [login (app (-> (mock/request :post "/auth/login") (mock/json-body {:email "ana@ex.com" :password "p"})))
          token (:token (decode-body login))
          auth (fn [req] (mock/header req "authorization" (str "Token " token)))

          ;; Create organization (user becomes admin automatically)
          org-resp (app (-> (mock/request :post "/api/organizations")
                            (mock/json-body {:name "Club"})
                            auth))
          org-id (:id (decode-body org-resp))]
      (is (= 201 (:status org-resp)))

        ;; Create pelada (user is admin, so can create)
      (let [resp (app (-> (mock/request :post "/api/peladas")
                          (mock/json-body {:organization_id org-id})
                          auth))]
        (is (= 201 (:status resp))))

        ;; Create teams (user is admin)
      (doseq [n ["A" "B" "C" "D"]]
        (is (= 201 (:status (app (-> (mock/request :post "/api/teams")
                                     (mock/json-body {:pelada_id 1 :name n})
                                     auth))))))

        ;; Close attendance
      (is (= 200 (:status (app (-> (mock/request :post "/api/peladas/1/close-attendance") auth)))))

        ;; Begin pelada (user is admin, can begin)
      (let [resp (app (-> (mock/request :post "/api/peladas/1/begin") auth))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (pos? (:matches_created body))))

        ;; List matches (user is member, can view)
      (let [resp (app (-> (mock/request :get "/api/peladas/1/matches") auth))
            body (decode-body resp)]
        (is (= 200 (:status resp)))
        (is (seq body))))))
