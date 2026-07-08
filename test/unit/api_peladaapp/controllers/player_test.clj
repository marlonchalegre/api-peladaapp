(ns api-peladaapp.controllers.player-test
  (:require
   [api-peladaapp.controllers.player :as controller.player]
   [api-peladaapp.db.player :as db.player]
   [clojure.test :refer [deftest is testing]]))

(deftest test-create-player-controller
  (let [db "dummy-db"
        player-uuid (random-uuid)
        org-uuid (random-uuid)
        user-uuid (random-uuid)
        valid-player {:organization-id org-uuid :user-id user-uuid :member-type "mensalista" :grade 5.0}
        invalid-player (assoc valid-player :member-type "mensalista_temporario")]
    (testing "create player successfully"
      (with-redefs [db.player/insert-player (fn [p _]
                                              (is (= "mensalista" (:member-type p)))
                                              player-uuid)
                    db.player/get-player (fn [id _]
                                           (is (= player-uuid id))
                                           (assoc valid-player :id player-uuid))]
        (let [resp (controller.player/create-player valid-player db)]
          (is (= player-uuid (:id resp))))))

    (testing "create player throws for temporary member type"
      (is (thrown-with-msg? Exception #"Temporary member types cannot be assigned directly"
                            (controller.player/create-player invalid-player db))))))

(deftest test-get-player-controller
  (let [db "dummy-db"
        player-uuid (random-uuid)]
    (testing "get player successfully when exists"
      (with-redefs [db.player/get-player (fn [id _] {:id id})]
        (is (= player-uuid (:id (controller.player/get-player player-uuid db))))))

    (testing "get player throws not found when nil"
      (with-redefs [db.player/get-player (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Player not found"
                              (controller.player/get-player player-uuid db)))))))

(deftest test-update-player-controller
  (let [db "dummy-db"
        player-uuid (random-uuid)
        update-data {:member-type "diarista" :passing 3}]
    (testing "update player successfully"
      (with-redefs [db.player/update-player (fn [id _p _]
                                              (is (= player-uuid id))
                                              1)
                    db.player/get-player (fn [id _] {:id id :member-type "diarista"})]
        (is (= "diarista" (:member-type (controller.player/update-player player-uuid update-data db))))))

    (testing "update player throws not found when affected rows is 0"
      (with-redefs [db.player/update-player (fn [_ _ _] 0)]
        (is (thrown-with-msg? Exception #"Player not found"
                              (controller.player/update-player player-uuid update-data db)))))))

(deftest test-delete-player-controller
  (let [db "dummy-db"
        player-uuid (random-uuid)]
    (testing "delete player successfully"
      (with-redefs [db.player/delete-player (fn [id _]
                                              (is (= player-uuid id))
                                              1)]
        (is (= 1 (controller.player/delete-player player-uuid db)))))

    (testing "delete player throws not found when affected rows is 0"
      (with-redefs [db.player/delete-player (fn [_ _] 0)]
        (is (thrown-with-msg? Exception #"Player not found"
                              (controller.player/delete-player player-uuid db)))))))

(deftest test-list-players-controller
  (let [db "dummy-db"
        org-uuid (random-uuid)]
    (with-redefs [db.player/list-players-by-organization (fn [id _]
                                                           (is (= org-uuid id))
                                                           [{:id 1}])]
      (is (= 1 (count (controller.player/list-players org-uuid db)))))))
