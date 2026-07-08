(ns api-peladaapp.controllers.team-test
  (:require
   [api-peladaapp.controllers.team :as controller.team]
   [api-peladaapp.db.team :as db.team]
   [clojure.test :refer [deftest is testing]]))

(deftest test-create-team
  (let [db "dummy-db"
        team-uuid (random-uuid)
        pelada-uuid (random-uuid)
        valid-team {:pelada-id pelada-uuid :name "Team A"}]
    (with-redefs [db.team/insert-team (fn [t _]
                                        (is (= "Team A" (:name t)))
                                        team-uuid)
                  db.team/get-team (fn [id _]
                                     (is (= team-uuid id))
                                     (assoc valid-team :id team-uuid))]
      (let [resp (controller.team/create-team valid-team db)]
        (is (= team-uuid (:id resp)))))))

(deftest test-get-team
  (let [db "dummy-db"
        team-uuid (random-uuid)]
    (testing "get team successfully when exists"
      (with-redefs [db.team/get-team (fn [id _] {:id id})]
        (is (= team-uuid (:id (controller.team/get-team team-uuid db))))))

    (testing "get team throws not found when nil"
      (with-redefs [db.team/get-team (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Team not found"
                              (controller.team/get-team team-uuid db)))))))

(deftest test-update-team
  (let [db "dummy-db"
        team-uuid (random-uuid)
        update-data {:name "Team B"}]
    (testing "update team successfully"
      (with-redefs [db.team/update-team (fn [id _t _]
                                          (is (= team-uuid id))
                                          1)
                    db.team/get-team (fn [id _] {:id id :name "Team B"})]
        (is (= "Team B" (:name (controller.team/update-team team-uuid update-data db))))))

    (testing "update team throws not found when affected rows is 0"
      (with-redefs [db.team/update-team (fn [_ _ _] 0)]
        (is (thrown-with-msg? Exception #"Team not found"
                              (controller.team/update-team team-uuid update-data db)))))))

(deftest test-delete-team
  (let [db "dummy-db"
        team-uuid (random-uuid)]
    (testing "delete team successfully"
      (with-redefs [db.team/delete-team (fn [id _]
                                          (is (= team-uuid id))
                                          1)]
        (is (= 1 (controller.team/delete-team team-uuid db)))))

    (testing "delete team throws not found when affected rows is 0"
      (with-redefs [db.team/delete-team (fn [_ _] 0)]
        (is (thrown-with-msg? Exception #"Team not found"
                              (controller.team/delete-team team-uuid db)))))))

(deftest test-list-teams
  (let [db "dummy-db"
        pelada-uuid (random-uuid)]
    (with-redefs [db.team/list-pelada-teams (fn [id _]
                                              (is (= pelada-uuid id))
                                              [{:id 1}])]
      (is (= 1 (count (controller.team/list-teams pelada-uuid db)))))))

(deftest test-add-player
  (let [db "dummy-db"
        team-uuid (random-uuid)
        player-uuid (random-uuid)]
    (testing "add player defaults"
      (with-redefs [db.team/add-player-to-team (fn [t-id p-id is-gk _]
                                                 (is (= team-uuid t-id))
                                                 (is (= player-uuid p-id))
                                                 (is (false? is-gk))
                                                 1)]
        (is (= {:team-id team-uuid :player-id player-uuid :is-goalkeeper false}
               (controller.team/add-player team-uuid player-uuid db)))))

    (testing "add player specifying goalkeeper"
      (with-redefs [db.team/add-player-to-team (fn [_t-id _p-id is-gk _]
                                                 (is (true? is-gk))
                                                 1)]
        (is (= {:team-id team-uuid :player-id player-uuid :is-goalkeeper true}
               (controller.team/add-player team-uuid player-uuid true db)))))))

(deftest test-remove-player
  (let [db "dummy-db"
        team-uuid (random-uuid)
        player-uuid (random-uuid)]
    (with-redefs [db.team/remove-player-from-team (fn [t-id p-id _]
                                                    (is (= team-uuid t-id))
                                                    (is (= player-uuid p-id))
                                                    1)]
      (is (= 1 (controller.team/remove-player team-uuid player-uuid db))))))
