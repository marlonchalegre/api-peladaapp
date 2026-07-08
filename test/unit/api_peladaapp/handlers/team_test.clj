(ns api-peladaapp.handlers.team-test
  (:require
   [api-peladaapp.controllers.pelada :as controller.pelada]
   [api-peladaapp.controllers.team :as controller.team]
   [api-peladaapp.handlers.team :as handler.team]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.test :refer [deftest is testing]]))

(deftest test-create-handler
  (let [db "dummy-db"
        user-uuid (random-uuid)
        org-uuid (random-uuid)
        pelada-uuid (random-uuid)
        team-uuid (random-uuid)
        new-team {:id team-uuid :name "Team A" :pelada-id pelada-uuid}]
    (testing "create team successfully"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-uuid)
                    controller.pelada/get-pelada (fn [p-id _]
                                                   (is (= pelada-uuid p-id))
                                                   {:organization-id org-uuid})
                    auth/require-organization-admin! (fn [u-id o-id _]
                                                       (is (= user-uuid u-id))
                                                       (is (= org-uuid o-id))
                                                       true)
                    controller.team/create-team (fn [team _]
                                                  (is (= "Team A" (:name team)))
                                                  new-team)]
        (let [request {:database db
                       :body {:name "Team A" :pelada_id (str pelada-uuid)}
                       :identity {:id user-uuid}}
              response (handler.team/create request)]
          (is (= 201 (:status response)))
          (is (= (:id new-team) (get-in response [:body :id]))))))

    (testing "create team exception handler catches exception"
      (with-redefs [auth/get-user-id-from-request (fn [_] (throw (Exception. "Unexpected error")))]
        (let [response (handler.team/create {:database db})]
          (is (= 500 (:status response))))))))

(deftest test-delete-handler
  (let [db "dummy-db"
        user-uuid (random-uuid)
        org-uuid (random-uuid)
        pelada-uuid (random-uuid)
        team-uuid (random-uuid)]
    (testing "delete team successfully"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-uuid)
                    controller.team/get-team (fn [t-id _]
                                               (is (= team-uuid t-id))
                                               {:pelada-id pelada-uuid})
                    controller.pelada/get-pelada (fn [p-id _]
                                                   (is (= pelada-uuid p-id))
                                                   {:organization-id org-uuid})
                    auth/require-organization-admin! (fn [u-id o-id _]
                                                       (is (= user-uuid u-id))
                                                       (is (= org-uuid o-id))
                                                       true)
                    controller.team/delete-team (fn [t-id _]
                                                  (is (= team-uuid t-id))
                                                  1)]
        (let [request {:database db
                       :params {:id (str team-uuid)}
                       :identity {:id user-uuid}}
              response (handler.team/delete request)]
          (is (= 200 (:status response))))))))

(deftest test-add-player-handler
  (let [db "dummy-db"
        user-uuid (random-uuid)
        org-uuid (random-uuid)
        pelada-uuid (random-uuid)
        team-uuid (random-uuid)
        player-uuid (random-uuid)]
    (testing "add player successfully"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-uuid)
                    controller.team/get-team (fn [t-id _]
                                               (is (= team-uuid t-id))
                                               {:pelada-id pelada-uuid})
                    controller.pelada/get-pelada (fn [p-id _]
                                                   (is (= pelada-uuid p-id))
                                                   {:organization-id org-uuid})
                    auth/require-organization-admin! (fn [u-id o-id _]
                                                       (is (= user-uuid u-id))
                                                       (is (= org-uuid o-id))
                                                       true)
                    controller.team/add-player (fn [t-id p-id is-gk _]
                                                 (is (= team-uuid t-id))
                                                 (is (= player-uuid p-id))
                                                 (is (true? is-gk))
                                                 {:team-id team-uuid :player-id player-uuid :is-goalkeeper true})]
        (let [request {:database db
                       :params {:id (str team-uuid)}
                       :body {:player_id (str player-uuid) :is_goalkeeper true}
                       :identity {:id user-uuid}}
              response (handler.team/add-player request)]
          (is (= 201 (:status response)))
          (is (= (:team-id (:body response)) team-uuid)))))))

(deftest test-remove-player-handler
  (let [db "dummy-db"
        user-uuid (random-uuid)
        org-uuid (random-uuid)
        pelada-uuid (random-uuid)
        team-uuid (random-uuid)
        player-uuid (random-uuid)]
    (testing "remove player successfully"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-uuid)
                    controller.team/get-team (fn [t-id _]
                                               (is (= team-uuid t-id))
                                               {:pelada-id pelada-uuid})
                    controller.pelada/get-pelada (fn [p-id _]
                                                   (is (= pelada-uuid p-id))
                                                   {:organization-id org-uuid})
                    auth/require-organization-admin! (fn [u-id o-id _]
                                                       (is (= user-uuid u-id))
                                                       (is (= org-uuid o-id))
                                                       true)
                    controller.team/remove-player (fn [t-id p-id _]
                                                    (is (= team-uuid t-id))
                                                    (is (= player-uuid p-id))
                                                    1)]
        (let [request {:database db
                       :params {:id (str team-uuid)}
                       :body {:player_id (str player-uuid)}
                       :identity {:id user-uuid}}
              response (handler.team/remove-player request)]
          (is (= 200 (:status response))))))))
