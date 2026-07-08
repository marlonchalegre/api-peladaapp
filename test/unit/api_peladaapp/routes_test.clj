(ns api-peladaapp.routes-test
  (:require
   [api-peladaapp.routes :as routes]
   [clojure.test :refer [deftest is testing]]))

(deftest test-any-access
  (is (true? (routes/any-access {})))
  (is (true? (routes/any-access {:some "request"}))))

(deftest test-internal-access
  (testing "allows loopback and local addresses"
    (is (true? (routes/internal-access {:remote-addr "127.0.0.1"})))
    (is (true? (routes/internal-access {:remote-addr "localhost"})))
    (is (true? (routes/internal-access {:remote-addr "0:0:0:0:0:0:0:1"}))))

  (testing "allows private network addresses"
    (is (true? (routes/internal-access {:remote-addr "10.0.0.1"})))
    (is (true? (routes/internal-access {:remote-addr "172.16.0.1"})))
    (is (true? (routes/internal-access {:remote-addr "192.168.1.1"}))))

  (testing "denies public/other addresses"
    (is (false? (routes/internal-access {:remote-addr "8.8.8.8"})))
    (is (false? (routes/internal-access {:remote-addr "192.169.1.1"})))
    (is (false? (routes/internal-access {})))
    (is (false? (routes/internal-access {:remote-addr nil})))))
