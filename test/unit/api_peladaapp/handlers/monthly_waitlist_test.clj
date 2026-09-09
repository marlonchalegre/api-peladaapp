(ns api-peladaapp.handlers.monthly-waitlist-test
  (:require
   [api-peladaapp.db.monthly-waitlist :as db.monthly-waitlist]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.handlers.monthly-waitlist :as handler.monthly-waitlist]
   [api-peladaapp.logic.authorization :as auth]
   [api-peladaapp.logic.monthly-waitlist :as logic.monthly-waitlist]
   [clojure.test :refer [deftest is testing]]))

(def mock-org-id (random-uuid))
(def mock-admin-id (random-uuid))
(def mock-user-id (random-uuid))
(def mock-player-id (random-uuid))
(def other-player-id (random-uuid))

(deftest list-waitlist-handler-test
  (testing "Returns 403 when user is not organization admin"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-admin! (fn [_ _ _]
                                                     (throw (ex-info "Forbidden" {:type :forbidden})))]
      (let [req {:params {:organization_id (str mock-org-id)}}
            resp (handler.monthly-waitlist/list-waitlist req)]
        (is (= 403 (:status resp))))))

  (testing "Returns 200 and waitlist items when user is organization admin"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-admin-id)
                  auth/require-organization-admin! (fn [_ _ _] true)
                  db.monthly-waitlist/list-waitlist-by-org (fn [_ _]
                                                             [{:id (random-uuid)
                                                               :organization_id mock-org-id
                                                               :player_id mock-player-id
                                                               :user_id mock-user-id
                                                               :user_name "Diarista"
                                                               :created_at "2026-09-09T12:00:00Z"}])]
      (let [req {:params {:organization_id (str mock-org-id)}}
            resp (handler.monthly-waitlist/list-waitlist req)]
        (is (= 200 (:status resp)))
        (is (= 1 (count (:body resp))))))))

(deftest get-my-status-handler-test
  (testing "Returns 403 when user is not an organization member"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-member! (fn [_ _ _]
                                                      (throw (ex-info "Forbidden" {:type :forbidden})))]
      (let [req {:params {:organization_id (str mock-org-id)}}
            resp (handler.monthly-waitlist/get-my-status req)]
        (is (= 403 (:status resp))))))

  (testing "Returns 200 with status when user is a member"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-member! (fn [_ _ _] true)
                  logic.monthly-waitlist/get-waitlist-status (fn [_ _ _] {:in-queue true})]
      (let [req {:params {:organization_id (str mock-org-id)}}
            resp (handler.monthly-waitlist/get-my-status req)]
        (is (= 200 (:status resp)))
        (is (= true (:in_queue (:body resp))))))))

(deftest add-candidate-handler-test
  (testing "Returns 400 when no target player found"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-member! (fn [_ _ _] true)
                  db.player/get-org-player-by-user-id (fn [_ _ _] nil)]
      (let [req {:params {:organization_id (str mock-org-id)}
                 :body {}}
            resp (handler.monthly-waitlist/add-candidate req)]
        (is (= 400 (:status resp))))))

  (testing "Returns 403 when non-admin tries to add another player"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-member! (fn [_ _ _] true)
                  db.player/get-org-player-by-user-id (fn [_ _ _] {:id mock-player-id})
                  auth/require-organization-admin! (fn [_ _ _]
                                                     (throw (ex-info "Forbidden" {:type :forbidden})))]
      (let [req {:params {:organization_id (str mock-org-id)}
                 :body {:player_id (str other-player-id)}}
            resp (handler.monthly-waitlist/add-candidate req)]
        (is (= 403 (:status resp))))))

  (testing "Returns 201 when member adds themselves"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-member! (fn [_ _ _] true)
                  db.player/get-org-player-by-user-id (fn [_ _ _] {:id mock-player-id})
                  logic.monthly-waitlist/add-candidate! (fn [_ _ _] {:id (random-uuid) :status :success})]
      (let [req {:params {:organization_id (str mock-org-id)}
                 :body {}}
            resp (handler.monthly-waitlist/add-candidate req)]
        (is (= 201 (:status resp)))))))

(deftest remove-candidate-handler-test
  (testing "Returns 403 when logic throws forbidden"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-member! (fn [_ _ _] true)
                  auth/user-can-admin-organization? (fn [_ _ _] false)
                  logic.monthly-waitlist/remove-candidate! (fn [_ _ _ _ _]
                                                             (throw (ex-info "Forbidden" {:type :forbidden :message "You cannot remove another player from the waitlist"})))]
      (let [req {:params {:organization_id (str mock-org-id)
                          :player_id (str other-player-id)}}
            resp (handler.monthly-waitlist/remove-candidate req)]
        (is (= 403 (:status resp))))))

  (testing "Returns 200 when non-admin removes themselves"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-member! (fn [_ _ _] true)
                  auth/user-can-admin-organization? (fn [_ _ _] false)
                  logic.monthly-waitlist/remove-candidate! (fn [_ _ _ _ _] {:status :success})]
      (let [req {:params {:organization_id (str mock-org-id)
                          :player_id (str mock-player-id)}}
            resp (handler.monthly-waitlist/remove-candidate req)]
        (is (= 200 (:status resp))))))

  (testing "Returns 200 when admin removes any player"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-admin-id)
                  auth/require-organization-member! (fn [_ _ _] true)
                  auth/user-can-admin-organization? (fn [_ _ _] true)
                  logic.monthly-waitlist/remove-candidate! (fn [_ _ _ _ _] {:status :success})]
      (let [req {:params {:organization_id (str mock-org-id)
                          :player_id (str other-player-id)}}
            resp (handler.monthly-waitlist/remove-candidate req)]
        (is (= 200 (:status resp)))))))

(deftest promote-candidate-handler-test
  (testing "Returns 403 when non-admin tries to promote"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-user-id)
                  auth/require-organization-admin! (fn [_ _ _]
                                                     (throw (ex-info "Forbidden" {:type :forbidden})))]
      (let [req {:params {:organization_id (str mock-org-id)
                          :player_id (str mock-player-id)}}
            resp (handler.monthly-waitlist/promote-candidate req)]
        (is (= 403 (:status resp))))))

  (testing "Returns 200 when admin promotes player"
    (with-redefs [auth/get-user-id-from-request (fn [_] mock-admin-id)
                  auth/require-organization-admin! (fn [_ _ _] true)
                  logic.monthly-waitlist/promote-candidate! (fn [_ _ _] {:status :success})]
      (let [req {:params {:organization_id (str mock-org-id)
                          :player_id (str mock-player-id)}}
            resp (handler.monthly-waitlist/promote-candidate req)]
        (is (= 200 (:status resp)))))))
