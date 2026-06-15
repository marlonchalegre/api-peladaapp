(ns api-peladaapp.notification-integration-test
  (:require
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.notifications :as notifications]
   [api-peladaapp.logic.waha :as waha]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]))

(use-fixtures :each th/test-system-fixture)

(deftest send-notification-with-mentions-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (db.organization/insert-organization {:name "Waha Mentions Org"} ds)]

    (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationWahaConfigs)
                                       (h/values [{:organization_id org-id
                                                   :enabled true
                                                   :api_url "http://waha:3000"
                                                   :instance "default"
                                                   :group_id "group123"
                                                   :attendance_reminder_enabled true
                                                   :use_all_mention true}]))))

    (testing "Send attendance reminder with mentions AND @all"
      (let [pending-players [{:player-name "User 1" :phone "5511911111111"}
                             {:player-name "User 2" :phone "5541922222222"}
                             {:player-name "User 3" :phone nil}]
            sent-payload (atom nil)]
        (with-redefs [waha/send-message (fn [_ message mentions]
                                          (reset! sent-payload {:message message :mentions mentions}))]
          (notifications/send-notification! org-id :attendance-reminder {:pending-players pending-players :pelada-id (parse-uuid "00000000-0000-0000-0000-000000000001")} ds)

          (is (some? @sent-payload))
          (let [{:keys [message mentions]} @sent-payload]
            ;; Check mentions list (JIDs) + "all"
            (is (= #{"5511911111111@c.us" "554122222222@c.us" "all"} (set mentions)))

            ;; Check message text
            (is (re-find #"⏰ \*Lembrete de Presença! @all\*" message))
            (is (re-find #"• User 1 \(@5511911111111\)" message))))))))

(deftest send-notification-disabled-all-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (db.organization/insert-organization {:name "Waha Disabled All Org"} ds)]

    (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationWahaConfigs)
                                       (h/values [{:organization_id org-id
                                                   :enabled true
                                                   :api_url "http://waha:3000"
                                                   :instance "default"
                                                   :group_id "group123"
                                                   :attendance_reminder_enabled true
                                                   :use_all_mention false}]))))

    (testing "Only individual mentions when use_all_mention is disabled"
      (let [pending-players [{:player-name "User 1" :phone "5511911111111"}]
            sent-payload (atom nil)]
        (with-redefs [waha/send-message (fn [_ message mentions]
                                          (reset! sent-payload {:message message :mentions mentions}))]
          (notifications/send-notification! org-id :attendance-reminder {:pending-players pending-players :pelada-id (parse-uuid "00000000-0000-0000-0000-000000000001")} ds)

          (is (some? @sent-payload))
          (is (= ["5511911111111@c.us"] (:mentions @sent-payload)))
          (is (not (re-find #"@all" (:message @sent-payload)))))))))

(deftest send-end-notification-with-results-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (db.organization/insert-organization {:name "Waha End Results Org"} ds)]

    (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationWahaConfigs)
                                       (h/values [{:organization_id org-id
                                                   :enabled true
                                                   :api_url "http://waha:3000"
                                                   :instance "default"
                                                   :group_id "group123"
                                                   :end_msg_enabled true}]))))

    (testing "Sends both summary and matches results messages on :end"
      (let [data {:pelada {:id (parse-uuid "00000000-0000-0000-0000-000000000001")
                           :scheduled-at "2023-01-01T10:00:00Z"}
                  :teams [{:id (parse-uuid "00000000-0000-0000-0000-000000000001") :name "Time A"}
                          {:id (parse-uuid "00000000-0000-0000-0000-000000000002") :name "Time B"}]
                  :matches [{:home-team-id (parse-uuid "00000000-0000-0000-0000-000000000001")
                             :away-team-id (parse-uuid "00000000-0000-0000-0000-000000000002")
                             :home-score 2 :away-score 1}]
                  :events []
                  :lineups []
                  :team-players []}
            sent-messages (atom [])]
        (with-redefs [waha/send-message (fn [_ message mentions]
                                          (swap! sent-messages conj {:message message :mentions mentions}))]
          (notifications/send-notification! org-id :end data ds)

          (is (= 2 (count @sent-messages)))
          (let [[msg1 msg2] @sent-messages]
            ;; First message check
            (is (re-find #"Resumo da rodada 01/01" (:message msg1)))
            (is (re-find #"Time A +3 pts" (:message msg1)))
            ;; Second message check
            (is (re-find #"⚽ \*RESULTADOS DAS PARTIDAS\*" (:message msg2)))
            (is (re-find #"    Time A  2 x 1  Time B" (:message msg2)))))))))

(deftest send-notification-waha-feature-flag-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (db.organization/insert-organization {:name "Waha Flag Test Org"} ds)]

    ;; Insert default feature flags (defaults to true in tests)
    (db.organization/insert-default-feature-flags org-id ds)

    ;; Setup WAHA config enabled for this org
    (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationWahaConfigs)
                                       (h/values [{:organization_id org-id
                                                   :enabled true
                                                   :api_url "http://waha:3000"
                                                   :instance "default"
                                                   :group_id "group123"
                                                   :attendance_reminder_enabled true
                                                   :use_all_mention false}]))))

    (testing "Does not send notifications if waha_communications feature flag is disabled"
      (db.organization/update-organization-feature-flags org-id {:waha_communications false} ds)
      (let [pending-players [{:player-name "User 1" :phone "5511911111111"}]
            sent-payload (atom nil)]
        (with-redefs [waha/send-message (fn [_ message mentions]
                                          (reset! sent-payload {:message message :mentions mentions}))]
          (notifications/send-notification! org-id :attendance-reminder {:pending-players pending-players :pelada-id (parse-uuid "00000000-0000-0000-0000-000000000001")} ds)
          (is (nil? @sent-payload)))))

    (testing "Sends notifications if waha_communications feature flag is enabled"
      (db.organization/update-organization-feature-flags org-id {:waha_communications true} ds)
      (let [pending-players [{:player-name "User 1" :phone "5511911111111"}]
            sent-payload (atom nil)]
        (with-redefs [waha/send-message (fn [_ message mentions]
                                          (reset! sent-payload {:message message :mentions mentions}))]
          (notifications/send-notification! org-id :attendance-reminder {:pending-players pending-players :pelada-id (parse-uuid "00000000-0000-0000-0000-000000000001")} ds)
          (is (some? @sent-payload))
          (is (= ["5511911111111@c.us"] (:mentions @sent-payload))))))))

(deftest send-new-pelada-notification-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (db.organization/insert-organization {:name "Waha New Pelada Org"} ds)]

    (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationWahaConfigs)
                                       (h/values [{:organization_id org-id
                                                   :enabled true
                                                   :api_url "http://waha:3000"
                                                   :instance "default"
                                                   :group_id "group123"
                                                   :attendance_reminder_enabled true
                                                   :use_all_mention true}]))))

    (testing "Send new pelada convocacao notification"
      (let [pelada-id (parse-uuid "00000000-0000-0000-0000-000000000001")
            sent-payload (atom nil)]
        (with-redefs [waha/send-message (fn [_ message mentions]
                                          (reset! sent-payload {:message message :mentions mentions}))]
          (notifications/send-notification! org-id :new-pelada {:pelada-id pelada-id :scheduled-at "2023-01-01T10:00:00Z" :confirmed-players []} ds)

          (is (some? @sent-payload))
          (let [{:keys [message mentions]} @sent-payload]
            (is (re-find #"⚽ \*Nova Pelada Confirmada! @all\*" message))
            (is (re-find #"01/01" message))
            (is (re-find #"/peladas/00000000-0000-0000-0000-000000000001" message))
            (is (re-find #"Nenhum jogador confirmado ainda." message))
            (is (= #{"all"} (set mentions)))))))))
