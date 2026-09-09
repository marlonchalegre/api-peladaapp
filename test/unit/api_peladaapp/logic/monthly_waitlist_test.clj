(ns api-peladaapp.logic.monthly-waitlist-test
  (:require
   [api-peladaapp.db.monthly-waitlist :as db.monthly-waitlist]
   [api-peladaapp.db.player :as db.player]
   [api-peladaapp.logic.monthly-waitlist :as logic.monthly-waitlist]
   [clojure.test :refer [deftest is testing]]))

(def mock-org-id (random-uuid))
(def other-org-id (random-uuid))
(def mock-player-id (random-uuid))
(def mock-user-id (random-uuid))
(def mock-waitlist-id (random-uuid))

(deftest add-candidate-test
  (testing "Throws :not-found if player does not exist"
    (with-redefs [db.player/get-player (fn [_ _] nil)]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player not found"
           (logic.monthly-waitlist/add-candidate! mock-org-id mock-player-id {})))))

  (testing "Throws :bad-request if player belongs to a different organization"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id other-org-id})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player does not belong to this organization"
           (logic.monthly-waitlist/add-candidate! mock-org-id mock-player-id {})))))

  (testing "Throws :bad-request if player is already a mensalista"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id mock-org-id :member-type "mensalista"})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player is already a monthly player"
           (logic.monthly-waitlist/add-candidate! mock-org-id mock-player-id {})))))

  (testing "Throws :bad-request if player is already in the waitlist"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id mock-org-id :member-type "diarista"})
                  db.monthly-waitlist/get-waitlist-entry (fn [_ _ _] {:id mock-waitlist-id})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player is already in the waitlist"
           (logic.monthly-waitlist/add-candidate! mock-org-id mock-player-id {})))))

  (testing "Successfully enqueues player when eligible"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id mock-org-id :member-type "diarista"})
                  db.monthly-waitlist/get-waitlist-entry (fn [_ _ _] nil)
                  db.monthly-waitlist/add-to-waitlist! (fn [_ _ _] mock-waitlist-id)]
      (is (= {:id mock-waitlist-id :status :success}
             (logic.monthly-waitlist/add-candidate! mock-org-id mock-player-id {}))))))

(deftest remove-candidate-test
  (testing "Throws :not-found if player does not exist"
    (with-redefs [db.player/get-player (fn [_ _] nil)]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player not found"
           (logic.monthly-waitlist/remove-candidate! mock-org-id mock-player-id mock-user-id false {})))))

  (testing "Throws :bad-request if player belongs to a different organization"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id other-org-id})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player does not belong to this organization"
           (logic.monthly-waitlist/remove-candidate! mock-org-id mock-player-id mock-user-id false {})))))

  (testing "Throws :forbidden if user is not admin and trying to remove another player"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id mock-org-id :user-id (random-uuid)})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Forbidden"
           (logic.monthly-waitlist/remove-candidate! mock-org-id mock-player-id mock-user-id false {})))))

  (testing "Successfully removes candidate when user removes themselves"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id mock-org-id :user-id mock-user-id})
                  db.monthly-waitlist/remove-from-waitlist! (fn [_ _ _] 1)]
      (is (= {:status :success}
             (logic.monthly-waitlist/remove-candidate! mock-org-id mock-player-id mock-user-id false {})))))

  (testing "Successfully removes candidate when admin removes any player"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id mock-org-id :user-id (random-uuid)})
                  db.monthly-waitlist/remove-from-waitlist! (fn [_ _ _] 1)]
      (is (= {:status :success}
             (logic.monthly-waitlist/remove-candidate! mock-org-id mock-player-id mock-user-id true {}))))))

(deftest promote-candidate-test
  (testing "Throws :not-found if player does not exist"
    (with-redefs [db.player/get-player (fn [_ _] nil)]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player not found"
           (logic.monthly-waitlist/promote-candidate! mock-org-id mock-player-id {})))))

  (testing "Throws :bad-request if player belongs to a different organization"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id other-org-id})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player does not belong to this organization"
           (logic.monthly-waitlist/promote-candidate! mock-org-id mock-player-id {})))))

  (testing "Throws :bad-request if player is not in waitlist"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id mock-org-id})
                  db.monthly-waitlist/get-waitlist-entry (fn [_ _ _] nil)]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Player is not in the waitlist"
           (logic.monthly-waitlist/promote-candidate! mock-org-id mock-player-id {})))))

  (testing "Successfully promotes candidate"
    (with-redefs [db.player/get-player (fn [_ _] {:id mock-player-id :organization-id mock-org-id})
                  db.monthly-waitlist/get-waitlist-entry (fn [_ _ _] {:id mock-waitlist-id})
                  db.monthly-waitlist/promote-player! (fn [_ _ _] true)]
      (is (= {:status :success}
             (logic.monthly-waitlist/promote-candidate! mock-org-id mock-player-id {}))))))

(deftest get-waitlist-status-test
  (testing "Returns in-queue: false when user is not a player in organization"
    (with-redefs [db.player/get-org-player-by-user-id (fn [_ _ _] nil)]
      (is (= {:in-queue false}
             (logic.monthly-waitlist/get-waitlist-status mock-org-id mock-user-id {})))))

  (testing "Returns in-queue: false when user is a player but not on waitlist"
    (with-redefs [db.player/get-org-player-by-user-id (fn [_ _ _] {:id mock-player-id})
                  db.monthly-waitlist/get-waitlist-entry (fn [_ _ _] nil)]
      (is (= {:in-queue false}
             (logic.monthly-waitlist/get-waitlist-status mock-org-id mock-user-id {})))))

  (testing "Returns in-queue: true with entry when player is on waitlist"
    (with-redefs [db.player/get-org-player-by-user-id (fn [_ _ _] {:id mock-player-id})
                  db.monthly-waitlist/get-waitlist-entry (fn [_ _ _] {:id mock-waitlist-id
                                                                      :organization_id mock-org-id
                                                                      :player_id mock-player-id
                                                                      :created_at "2026-09-09T10:00:00Z"})]
      (let [res (logic.monthly-waitlist/get-waitlist-status mock-org-id mock-user-id {})]
        (is (= true (:in-queue res)))
        (is (= mock-waitlist-id (get-in res [:entry :id])))))))
