(ns api-peladaapp.handlers.manual-stats-test
  (:require
   [api-peladaapp.controllers.manual-stats :as controller.manual-stats]
   [api-peladaapp.handlers.manual-stats :as handler.manual-stats]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.test :refer [deftest is testing]]))

(deftest test-upsert-manual-stats
  (let [db "dummy-db"
        user-uuid (random-uuid)
        org-uuid (random-uuid)]
    (testing "upsert manual stats successfully"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-uuid)
                    controller.manual-stats/upsert-manual-stats (fn [u-id o-id stats _]
                                                                  (is (= user-uuid u-id))
                                                                  (is (= org-uuid o-id))
                                                                  (is (= [{:player-id "p1" :goals 3}] stats))
                                                                  1)]
        (let [request {:database db
                       :params {:id (str org-uuid)}
                       :body [{:player_id "p1" :goals 3}]
                       :identity {:id user-uuid}}
              response (handler.manual-stats/upsert-manual-stats request)]
          (is (= 200 (:status response)))
          (is (= 1 (get-in response [:body :updated]))))))

    (testing "upsert manual stats handles exceptions"
      (with-redefs [auth/get-user-id-from-request (fn [_] (throw (Exception. "Upsert error")))]
        (let [response (handler.manual-stats/upsert-manual-stats {:database db})]
          (is (= 500 (:status response))))))))

(deftest test-list-manual-stats
  (let [db "dummy-db"
        org-uuid (random-uuid)]
    (testing "list manual stats successfully"
      (with-redefs [controller.manual-stats/list-manual-stats (fn [o-id year _]
                                                                (is (= org-uuid o-id))
                                                                (is (= 2023 year))
                                                                [{:id 1}])]
        (let [request {:database db
                       :params {:id (str org-uuid)}
                       :query-params {"year" "2023"}}
              response (handler.manual-stats/list-manual-stats request)]
          (is (= 200 (:status response)))
          (is (= 1 (count (:body response)))))))

    (testing "list manual stats with invalid year throws exception and returns bad-request"
      (let [request {:database db
                     :params {:id (str org-uuid)}
                     :query-params {"year" "invalid-year"}}
            response (handler.manual-stats/list-manual-stats request)]
        (is (= 400 (:status response)))))))
