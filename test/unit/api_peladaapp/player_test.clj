(ns api-peladaapp.player-test
  (:require
   [api-peladaapp.controllers.player :as controller.player]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.handlers.player :as handler.player]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.test :refer [deftest is testing]]))

(deftest update-player-test
  (let [db (fn [] nil)
        player-id (parse-uuid "00000000-0000-0000-0000-000000000001")
        org-id (parse-uuid "00000000-0000-0000-0000-000000000010")
        user-id (parse-uuid "00000000-0000-0000-0000-000000000002")
        admin-id (parse-uuid "00000000-0000-0000-0000-000000000099")
        mock-player {:id player-id :organization-id org-id :user-id user-id :grade 5.0}
        updated-player {:id player-id :organization-id org-id :user-id user-id :grade 7.5}]

    (testing "Successfully updates player score if user is org admin"
      (let [get-calls (atom 0)]
        (with-redefs [db.player/get-player (fn [id _]
                                             (if (= id player-id)
                                               (if (= @get-calls 0)
                                                 (do (swap! get-calls inc) mock-player)
                                                 updated-player)
                                               nil))
                      db.player/update-player (fn [_ _ _] 1)
                      auth/require-organization-admin! (fn [_ _ _] true)]
          (let [request {:database db
                         :params {:id player-id}
                         :body {:grade 7.5}
                         :identity {:id admin-id :is-global-admin? false}}
                response (handler.player/update-player-score request)]
            (is (= 200 (:status response)))
            (is (= 7.5 (:grade (:body response))))))))

    (testing "Successfully updates member_type if user is org admin"
      (let [get-calls (atom 0)
            mock-diarista {:id player-id :organization-id org-id :user-id user-id :member-type "diarista"}
            updated-mensalista {:id player-id :organization-id org-id :user-id user-id :member-type "mensalista"}]
        (with-redefs [db.player/get-player (fn [id _]
                                             (if (= id player-id)
                                               (if (= @get-calls 0)
                                                 (do (swap! get-calls inc) mock-diarista)
                                                 updated-mensalista)
                                               nil))
                      db.player/update-player (fn [_ update-data _]
                                                (is (= "mensalista" (:member-type update-data)))
                                                1)
                      auth/require-organization-admin! (fn [_ _ _] true)]
          (let [request {:database db
                         :params {:id player-id}
                         :body {:member_type "mensalista"}
                         :identity {:id admin-id :is-global-admin? false}}
                response (handler.player/update-player-score request)]
            (is (= 200 (:status response)))
            (is (= "mensalista" (:member_type (:body response))))))))

    (testing "Fails with 403 if user is not org admin"
      (with-redefs [db.player/get-player (fn [id _] (if (= id player-id) mock-player nil))
                    auth/require-organization-admin! (fn [_ _ _]
                                                       (throw (ex-info "Forbidden" {:type :forbidden})))]
        (let [request {:database db
                       :params {:id player-id}
                       :body {:grade 7.5}
                       :identity {:id admin-id :is-global-admin? false}}
              response (handler.player/update-player-score request)]
          (is (= 403 (:status response))))))

    (testing "Fails with 400 if trying to update member_type to a temporary type"
      (with-redefs [db.player/get-player (fn [id _] (if (= id player-id) mock-player nil))
                    auth/require-organization-admin! (fn [_ _ _] true)]
        (doseq [temp-role ["mensalista_temporario" "diarista_temporario"]]
          (let [request {:database db
                         :params {:id player-id}
                         :body {:member_type temp-role}
                         :identity {:id admin-id :is-global-admin? false}}
                response (handler.player/update-player-score request)]
            (is (= 400 (:status response)))
            (is (= "Temporary member types must be managed by the Substitution feature"
                   (:message (:body response))))))))

    (testing "Fails with 404 if player not found"
      (with-redefs [db.player/get-player (fn [_ _] nil)]
        (let [request {:database db
                       :params {:id (parse-uuid "00000000-0000-0000-0000-000000000999")}
                       :body {:grade 7.5}
                       :identity {:id admin-id :is-global-admin? false}}
              response (handler.player/update-player-score request)]
          (is (= 404 (:status response))))))

    (testing "Successfully updates player characteristics when feature flag is enabled"
      (let [get-calls (atom 0)
            mock-player-char {:id player-id :organization-id org-id :user-id user-id
                              :passing 2 :ball-control 3 :velocity 4 :shooting 5 :dribbling 1 :defending 0}
            updated-player-char {:id player-id :organization-id org-id :user-id user-id
                                 :passing 4 :ball-control 4 :velocity 4 :shooting 5 :dribbling 2 :defending 1}]
        (with-redefs [db.player/get-player (fn [id _]
                                             (if (= id player-id)
                                               (if (= @get-calls 0)
                                                 (do (swap! get-calls inc) mock-player-char)
                                                 updated-player-char)
                                               nil))
                      db.player/update-player (fn [_ update-data _]
                                                (is (= 4 (:passing update-data)))
                                                (is (= 4 (:ball-control update-data)))
                                                (is (= 2 (:dribbling update-data)))
                                                (is (= 1 (:defending update-data)))
                                                1)
                      auth/require-organization-admin! (fn [_ _ _] true)
                      auth/require-feature-flag! (fn [org-id flag _]
                                                   (is (= org-id org-id))
                                                   (is (= :player_characteristics flag))
                                                   true)]
          (let [request {:database db
                         :params {:id player-id}
                         :body {:passing 4 :ball_control 4 :dribbling 2 :defending 1}
                         :identity {:id admin-id :is-global-admin? false}}
                response (handler.player/update-player-score request)]
            (is (= 200 (:status response)))
            (is (= 4 (:passing (:body response))))
            (is (= 4 (:ball_control (:body response))))
            (is (= 2 (:dribbling (:body response))))
            (is (= 1 (:defending (:body response))))))))

    (testing "Fails with 400 if any characteristic is out of range 0-5"
      (with-redefs [db.player/get-player (fn [id _] (if (= id player-id) mock-player nil))
                    auth/require-organization-admin! (fn [_ _ _] true)]
        (let [request {:database db
                       :params {:id player-id}
                       :body {:passing 6}
                       :identity {:id admin-id :is-global-admin? false}}
              response (handler.player/update-player-score request)]
          (is (= 400 (:status response)))
          (is (= "passing must be between 0 and 5" (:message (:body response)))))
        (let [request {:database db
                       :params {:id player-id}
                       :body {:defending -1}
                       :identity {:id admin-id :is-global-admin? false}}
              response (handler.player/update-player-score request)]
          (is (= 400 (:status response)))
          (is (= "defending must be between 0 and 5" (:message (:body response)))))))))

(deftest create-player-handler-test
  (let [db (fn [] nil)
        user-id (random-uuid)
        org-id (random-uuid)
        new-player {:id (random-uuid) :organization-id org-id :user-id user-id}]
    (testing "create player successfully"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-id)
                    auth/require-organization-admin! (fn [u-id o-id _]
                                                       (is (= user-id u-id))
                                                       (is (= org-id o-id))
                                                       true)
                    auth/check-member-limit! (fn [o-id _]
                                               (is (= org-id o-id))
                                               true)
                    controller.player/create-player (fn [player _]
                                                      (is (= org-id (:organization-id player)))
                                                      new-player)]
        (let [request {:database db
                       :body {:organization_id org-id :user_id user-id}
                       :identity {:id user-id}}
              response (handler.player/create request)]
          (is (= 201 (:status response)))
          (is (= (:id new-player) (get-in response [:body :id]))))))

    (testing "create player failure catches exception"
      (with-redefs [auth/get-user-id-from-request (fn [_] (throw (Exception. "Unexpected error")))]
        (let [response (handler.player/create {:database db})]
          (is (= 500 (:status response))))))))

(deftest delete-player-handler-test
  (let [db (fn [] nil)
        player-uuid (random-uuid)
        org-uuid (random-uuid)
        admin-uuid (random-uuid)
        dummy-player {:id player-uuid :organization-id org-uuid}]
    (testing "delete player successfully"
      (with-redefs [auth/get-user-id-from-request (fn [_] admin-uuid)
                    controller.player/get-player (fn [id _]
                                                   (is (= player-uuid id))
                                                   dummy-player)
                    auth/require-organization-admin! (fn [u-id o-id _]
                                                       (is (= admin-uuid u-id))
                                                       (is (= org-uuid o-id))
                                                       true)
                    controller.player/delete-player (fn [id _]
                                                      (is (= player-uuid id))
                                                      1)]
        (let [request {:database db
                       :params {:id player-uuid}
                       :identity {:id admin-uuid}}
              response (handler.player/delete request)]
          (is (= 200 (:status response))))))))

(deftest list-by-org-player-handler-test
  (let [db (fn [] nil)
        org-uuid (random-uuid)
        user-uuid (random-uuid)
        players [{:id (random-uuid) :organization-id org-uuid :user-id (random-uuid)}]]
    (testing "list players by organization successfully"
      (with-redefs [auth/get-user-id-from-request (fn [_] user-uuid)
                    auth/require-organization-member! (fn [u-id o-id _]
                                                        (is (= user-uuid u-id))
                                                        (is (= org-uuid o-id))
                                                        true)
                    controller.player/list-players (fn [o-id _]
                                                     (is (= org-uuid o-id))
                                                     players)]
        (let [request {:database db
                       :params {:organization_id org-uuid}
                       :identity {:id user-uuid}}
              response (handler.player/list-by-org request)]
          (is (= 200 (:status response)))
          (is (= 1 (count (:body response)))))))))

