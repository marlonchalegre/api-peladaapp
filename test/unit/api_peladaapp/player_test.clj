(ns api-peladaapp.player-test
  (:require
   [api-peladaapp.controllers.player :as controller.player]
   [api-peladaapp.handlers.player :as handler.player]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.test :refer [deftest is testing]]))

(deftest update-player-test
  (let [db (fn [] nil)
        mock-player {:id 1 :organization-id 10 :user-id 2 :grade 5.0}
        updated-player {:id 1 :organization-id 10 :user-id 2 :grade 7.5}]

    (testing "Successfully updates player score if user is org admin"
      (let [get-calls (atom 0)]
        (with-redefs [db.player/get-player (fn [id _] 
                                             (if (= id 1) 
                                               (if (= @get-calls 0)
                                                 (do (swap! get-calls inc) mock-player)
                                                 updated-player)
                                               nil))
                      db.player/update-player (fn [id update-data _] 1)
                      auth/require-organization-admin! (fn [_ _ _] true)]
          (let [request {:database db
                         :params {:id "1"}
                         :body {:grade 7.5}
                         :identity {:id 99 :is-admin? false}}
                response (handler.player/update-player-score request)]
            (is (= 200 (:status response)))
            (is (= 7.5 (:grade (:body response))))))))

    (testing "Fails with 403 if user is not org admin"
      (with-redefs [db.player/get-player (fn [id _] (if (= id 1) mock-player nil))
                    auth/require-organization-admin! (fn [_ _ _] 
                                                       (throw (ex-info "Forbidden" {:type :forbidden})))]
        (let [request {:database db
                       :params {:id "1"}
                       :body {:grade 7.5}
                       :identity {:id 99 :is-admin? false}}
              response (handler.player/update-player-score request)]
          (is (= 403 (:status response))))))

    (testing "Fails with 404 if player not found"
      (with-redefs [db.player/get-player (fn [_ _] nil)]
        (let [request {:database db
                       :params {:id "999"}
                       :body {:grade 7.5}
                       :identity {:id 99 :is-admin? false}}
              response (handler.player/update-player-score request)]
          (is (= 404 (:status response))))))))
