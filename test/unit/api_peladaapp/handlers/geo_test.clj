(ns api-peladaapp.handlers.geo-test
  (:require
   [api-peladaapp.handlers.geo :as handler.geo]
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.test :refer [deftest is testing]]))

(deftest geo-search-validation-test
  (testing "returns empty list for nil or blank query"
    (let [res1 (handler.geo/search {:params {}})
          res2 (handler.geo/search {:params {:q ""}})
          res3 (handler.geo/search {:params {:q "   "}})]
      (is (= 200 (:status res1)))
      (is (= [] (:body res1)))
      (is (= [] (:body res2)))
      (is (= [] (:body res3)))))

  (testing "returns empty list for query with fewer than 3 characters"
    (let [res (handler.geo/search {:params {:q "ab"}})]
      (is (= 200 (:status res)))
      (is (= [] (:body res))))))

(deftest geo-search-upstream-and-cache-test
  (let [mock-results [{:place_id 12345
                       :display_name "Arena Vila Nova, Rua das Flores, São Paulo, Brasil"
                       :name "Arena Vila Nova"
                       :lat "-23.5505"
                       :lon "-46.6333"}]
        mock-json (json/write-str mock-results)
        calls (atom 0)]
    (with-redefs [http/get (fn [_ _]
                             (swap! calls inc)
                             {:status 200
                              :body mock-json})]
      (testing "fetches from upstream and formats fields"
        (let [res (handler.geo/search {:params {:q "Arena Vila Nova"}})]
          (is (= 200 (:status res)))
          (is (= 1 @calls))
          (is (= 1 (count (:body res))))
          (let [item (first (:body res))]
            (is (= 12345 (:place_id item)))
            (is (= "Arena Vila Nova, Rua das Flores, São Paulo, Brasil" (:display_name item)))
            (is (= "Arena Vila Nova" (:name item)))
            (is (= "-23.5505" (:lat item)))
            (is (= "-46.6333" (:lon item))))))

      (testing "returns cached result on subsequent query without hitting upstream again"
        (let [res (handler.geo/search {:params {:q "arena vila nova"}})]
          (is (= 200 (:status res)))
          (is (= 1 @calls) "Upstream should not have been called again")
          (is (= 1 (count (:body res)))))))))

(deftest geo-search-error-handling-test
  (with-redefs [http/get (fn [_ _]
                           (throw (java.net.SocketTimeoutException. "Read timed out")))]
    (testing "returns empty list on upstream failure without throwing"
      (let [res (handler.geo/search {:params {:q "Timeout Location Query"}})]
        (is (= 200 (:status res)))
        (is (= [] (:body res)))))))
