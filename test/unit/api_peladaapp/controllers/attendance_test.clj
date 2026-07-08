(ns api-peladaapp.controllers.attendance-test
  (:require
   [api-peladaapp.controllers.attendance :as controller.attendance]
   [api-peladaapp.db.attendance :as db.attendance]
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.db.vote :as db.vote]
   [api-peladaapp.logic.authorization :as auth]
   [clojure.test :refer [deftest is testing]]))

(deftest test-update-player-attendance
  (let [db "dummy-db"
        pelada-uuid (random-uuid)
        user-uuid (random-uuid)
        player-uuid (random-uuid)
        org-uuid (random-uuid)]
    (testing "throws when pelada not found"
      (with-redefs [db.pelada/get-pelada (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Pelada not found"
                              (controller.attendance/update-player-attendance pelada-uuid user-uuid nil "confirmed" db)))))

    (testing "target-player-id is present"
      (testing "requires organization admin and throws if player not found"
        (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid})
                      auth/require-organization-admin! (fn [u o _]
                                                         (is (= user-uuid u))
                                                         (is (= org-uuid o))
                                                         true)
                      db.player/get-org-player-by-user-id (fn [_ _ _] {:id player-uuid})
                      db.player/get-player (fn [_ _] nil)]
          (is (thrown-with-msg? Exception #"Player not found"
                                (controller.attendance/update-player-attendance pelada-uuid user-uuid player-uuid "confirmed" db)))))

      (testing "updates attendance successfully"
        (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid})
                      auth/require-organization-admin! (fn [_ _ _] true)
                      db.player/get-org-player-by-user-id (fn [_ _ _] {:id player-uuid})
                      db.player/get-player (fn [id _] {:id id :member-type "mensalista"})
                      db.attendance/upsert-attendance (fn [_pelada _player status _]
                                                        (is (= "confirmed" status))
                                                        1)]
          (is (= 1 (controller.attendance/update-player-attendance pelada-uuid user-uuid player-uuid "confirmed" db))))))

    (testing "target-player-id is nil"
      (testing "throws forbidden if user is not a player"
        (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid})
                      db.player/get-org-player-by-user-id (fn [_ _ _] nil)]
          (is (thrown-with-msg? Exception #"User is not a player"
                                (controller.attendance/update-player-attendance pelada-uuid user-uuid nil "confirmed" db)))))

      (testing "converts status to waitlist for non-mensalista"
        (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid})
                      db.player/get-org-player-by-user-id (fn [_ _ _] {:id player-uuid :member-type "diarista"})
                      db.attendance/upsert-attendance (fn [_pelada _player status _]
                                                        (is (= "waitlist" status))
                                                        1)]
          (is (= 1 (controller.attendance/update-player-attendance pelada-uuid user-uuid nil "confirmed" db)))))

      (testing "keeps status confirmed for mensalista"
        (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid})
                      db.player/get-org-player-by-user-id (fn [_ _ _] {:id player-uuid :member-type "mensalista"})
                      db.attendance/upsert-attendance (fn [_pelada _player status _]
                                                        (is (= "confirmed" status))
                                                        1)]
          (is (= 1 (controller.attendance/update-player-attendance pelada-uuid user-uuid nil "confirmed" db))))))))

(deftest test-update-attendance
  (let [db "dummy-db"
        pelada-uuid (random-uuid)
        player-uuid (random-uuid)]
    (with-redefs [db.attendance/upsert-attendance (fn [_ _ status _]
                                                    (is (= "confirmed" status))
                                                    1)]
      (is (= 1 (controller.attendance/update-attendance pelada-uuid player-uuid "confirmed" db))))))

(deftest test-batch-update-attendance
  (let [db "dummy-db"
        pelada-uuid (random-uuid)
        user-uuid (random-uuid)
        org-uuid (random-uuid)
        player-ids [(random-uuid)]]
    (testing "throws when pelada not found"
      (with-redefs [db.pelada/get-pelada (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Pelada not found"
                              (controller.attendance/batch-update-attendance pelada-uuid user-uuid player-ids "confirmed" db)))))

    (testing "succeeds when admin"
      (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid})
                    auth/require-organization-admin! (fn [_ _ _] true)
                    db.attendance/batch-upsert-attendance (fn [_pelada players status _]
                                                            (is (= player-ids players))
                                                            (is (= "confirmed" status))
                                                            1)]
        (is (= 1 (controller.attendance/batch-update-attendance pelada-uuid user-uuid player-ids "confirmed" db)))))))

(deftest test-close-attendance
  (let [db "dummy-db"
        pelada-uuid (random-uuid)
        user-uuid (random-uuid)
        org-uuid (random-uuid)]
    (testing "throws when pelada not found"
      (with-redefs [db.pelada/get-pelada (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Pelada not found"
                              (controller.attendance/close-attendance pelada-uuid user-uuid db)))))

    (testing "works when status is attendance"
      (with-redefs [db.pelada/get-pelada (fn [id _] (if (= id pelada-uuid)
                                                      {:organization-id org-uuid :status "attendance"}
                                                      {:organization-id org-uuid :status "open"}))
                    auth/require-organization-admin! (fn [_ _ _] true)
                    db.pelada/update-pelada (fn [_ data _]
                                              (is (= "open" (:status data)))
                                              1)]
        (let [resp (controller.attendance/close-attendance pelada-uuid user-uuid db)]
          (is (= "open" (:status resp))))))

    (testing "throws when status is not attendance"
      (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid :status "open"})
                    auth/require-organization-admin! (fn [_ _ _] true)]
        (is (thrown-with-msg? Exception #"Pelada is not in attendance status"
                              (controller.attendance/close-attendance pelada-uuid user-uuid db)))))))

(deftest test-get-player-attendance
  (let [db "dummy-db"
        pelada-uuid (random-uuid)
        player-uuid (random-uuid)
        mock-attendance [{:player_id player-uuid :status "confirmed"}
                         {:player_id (random-uuid) :status "waitlist"}]]
    (with-redefs [db.attendance/list-attendance-by-pelada (fn [_ _] mock-attendance)]
      (is (= "confirmed" (:status (controller.attendance/get-player-attendance pelada-uuid player-uuid db)))))))

(deftest test-update-voting-enabled
  (let [db "dummy-db"
        pelada-uuid (random-uuid)
        user-uuid (random-uuid)
        org-uuid (random-uuid)
        player-uuid (random-uuid)]
    (testing "throws when pelada not found"
      (with-redefs [db.pelada/get-pelada (fn [_ _] nil)]
        (is (thrown-with-msg? Exception #"Pelada not found"
                              (controller.attendance/update-voting-enabled pelada-uuid user-uuid player-uuid true db)))))

    (testing "when enabled is true"
      (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid})
                    auth/require-organization-admin! (fn [_ _ _] true)
                    db.attendance/update-voting-enabled (fn [_ _ enabled _tx]
                                                          (is (true? enabled))
                                                          1)]
        (is (= {:updated 1} (controller.attendance/update-voting-enabled pelada-uuid user-uuid player-uuid true db)))))

    (testing "when enabled is false, deletes votes"
      (let [votes-deleted (atom false)]
        (with-redefs [db.pelada/get-pelada (fn [_ _] {:organization-id org-uuid})
                      auth/require-organization-admin! (fn [_ _ _] true)
                      db.attendance/update-voting-enabled (fn [_ _ enabled _tx]
                                                            (is (false? enabled))
                                                            1)
                      db.vote/delete-votes-for-target (fn [pelada player _tx]
                                                        (is (= pelada-uuid pelada))
                                                        (is (= player-uuid player))
                                                        (reset! votes-deleted true)
                                                        1)]
          (is (= {:updated 1} (controller.attendance/update-voting-enabled pelada-uuid user-uuid player-uuid false db)))
          (is @votes-deleted))))))
