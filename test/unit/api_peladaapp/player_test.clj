(ns api-peladaapp.player-test
  (:require
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
                         :identity {:id admin-id :is-admin? false}}
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
                         :identity {:id admin-id :is-admin? false}}
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
                       :identity {:id admin-id :is-admin? false}}
              response (handler.player/update-player-score request)]
          (is (= 403 (:status response))))))

    (testing "Fails with 400 if trying to update member_type to a temporary type"
      (with-redefs [db.player/get-player (fn [id _] (if (= id player-id) mock-player nil))
                    auth/require-organization-admin! (fn [_ _ _] true)]
        (doseq [temp-role ["mensalista_temporario" "diarista_temporario"]]
          (let [request {:database db
                         :params {:id player-id}
                         :body {:member_type temp-role}
                         :identity {:id admin-id :is-admin? false}}
                response (handler.player/update-player-score request)]
            (is (= 400 (:status response)))
            (is (= "Temporary member types must be managed by the Substitution feature"
                   (:message (:body response))))))))

    (testing "Fails with 404 if player not found"
      (with-redefs [db.player/get-player (fn [_ _] nil)]
        (let [request {:database db
                       :params {:id (parse-uuid "00000000-0000-0000-0000-000000000999")}
                       :body {:grade 7.5}
                       :identity {:id admin-id :is-admin? false}}
              response (handler.player/update-player-score request)]
          (is (= 404 (:status response))))))))
