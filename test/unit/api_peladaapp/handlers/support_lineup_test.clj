(ns api-peladaapp.handlers.support-lineup-test
  (:require
   [api-peladaapp.controllers.match :as match-controller]
   [api-peladaapp.controllers.pelada :as pelada-controller]
   [api-peladaapp.handlers.match :as handler.match]
   [api-peladaapp.handlers.pelada :as handler.pelada]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.pelada :as pelada-logic]
   [clojure.test :refer [deftest is testing]]))

(deftest test-update-support-lineup-handler
  (let [match-id (random-uuid)
        pelada-id (random-uuid)
        org-id (random-uuid)
        user-id (random-uuid)
        cam-id (random-uuid)
        stats-id (random-uuid)]
    (testing "successfully updates support lineup"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-id)
                    match-controller/get-match (fn [_ _] {:id match-id :pelada-id pelada-id})
                    pelada-controller/get-pelada (fn [_ _] {:id pelada-id :organization-id org-id :status "running"})
                    auth/require-organization-admin! (fn [_ _ _] true)
                    pelada-logic/ensure-running (fn [p & _] p)
                    match-controller/update-support-lineup (fn [m-id data _]
                                                             (is (= match-id m-id))
                                                             (is (= cam-id (:support-camera-player-id data)))
                                                             (is (= stats-id (:support-stats-player-id data)))
                                                             {:id match-id :pelada-id pelada-id :sequence 1
                                                              :home-team-id (random-uuid) :away-team-id (random-uuid)
                                                              :support-camera-player-id cam-id :support-stats-player-id stats-id})]
        (let [req {:database "dummy-db"
                   :params {:id (str match-id)}
                   :body {:support_camera_player_id (str cam-id)
                          :support_stats_player_id (str stats-id)}}
              resp (handler.match/update-support-lineup req)]
          (is (= 200 (:status resp)))
          (is (= cam-id (get-in resp [:body :support_camera_player_id])))
          (is (= stats-id (get-in resp [:body :support_stats_player_id]))))))))

(deftest test-reroll-support-lineup-handler
  (let [match-id (random-uuid)
        pelada-id (random-uuid)
        org-id (random-uuid)
        user-id (random-uuid)
        cam-id (random-uuid)]
    (testing "successfully rerolls support lineup"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-id)
                    match-controller/get-match (fn [_ _] {:id match-id :pelada-id pelada-id})
                    pelada-controller/get-pelada (fn [_ _] {:id pelada-id :organization-id org-id :status "running"})
                    auth/require-organization-admin! (fn [_ _ _] true)
                    pelada-logic/ensure-running (fn [p & _] p)
                    match-controller/reroll-support-lineup (fn [m-id _]
                                                             (is (= match-id m-id))
                                                             {:id match-id :pelada-id pelada-id :sequence 1
                                                              :home-team-id (random-uuid) :away-team-id (random-uuid)
                                                              :support-camera-player-id cam-id :support-stats-player-id nil})]
        (let [req {:database "dummy-db"
                   :params {:id (str match-id)}}
              resp (handler.match/reroll-support-lineup req)]
          (is (= 200 (:status resp)))
          (is (= cam-id (get-in resp [:body :support_camera_player_id]))))))))

(deftest test-generate-support-lineup-handler
  (let [pelada-id (random-uuid)
        org-id (random-uuid)
        user-id (random-uuid)
        match-id (random-uuid)]
    (testing "successfully generates support lineup for pelada"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-id)
                    pelada-controller/get-pelada (fn [_ _] {:id pelada-id :organization-id org-id :status "running"})
                    auth/require-organization-admin! (fn [_ _ _] true)
                    pelada-controller/generate-support-lineup (fn [p-id _]
                                                                (is (= pelada-id p-id))
                                                                [{:id match-id :pelada-id pelada-id :sequence 1
                                                                  :home-team-id (random-uuid) :away-team-id (random-uuid)
                                                                  :support-camera-player-id (random-uuid)}])]
        (let [req {:database "dummy-db"
                   :params {:id (str pelada-id)}}
              resp (handler.pelada/generate-support-lineup req)]
          (is (= 200 (:status resp)))
          (is (= 1 (count (:body resp)))))))))
