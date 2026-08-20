(ns api-peladaapp.notification-integration-test
  (:require
   [api-peladaapp.controllers.organization :as controller.organization]
   [api-peladaapp.db.organization :as db.organization]
   [api-peladaapp.db.user :as db.user]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.logic.notifications :as notifications]
   [api-peladaapp.logic.waha :as waha]
   [api-peladaapp.test-helpers :as th]
   [clojure.string :as str]
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
            (is (re-find #"• @5511911111111 \(User 1\)" message))))))))

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
            ;; First message check (Round Summary)
            (is (re-find #"Resumo da rodada" (:message msg1)))
            (is (re-find #"Classificacao:" (:message msg1)))
            (is (re-find #"Time A" (:message msg1)))
            ;; Second message check (Matches Results)
            (is (re-find #"RESULTADOS DAS PARTIDAS" (:message msg2)))
            (is (re-find #"Time A  2 x 1  Time B" (:message msg2)))))))))

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

(deftest manual-notification-send-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (db.organization/insert-organization {:name "Manual Notification Org"} ds)]

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

    (testing "Send custom message successfully"
      (let [sent-msg (atom nil)]
        (with-redefs [waha/send-message (fn [_ msg mentions] (reset! sent-msg {:msg msg :mentions mentions}))]
          (let [res (controller.organization/send-custom-message org-id "Hello Pelada!" ds)]
            (is (= "success" (:status res)))
            (is (= "Hello Pelada!" (:msg @sent-msg)))))))

    (testing "Resend notification"
      (let [pelada-id (parse-uuid "00000000-0000-0000-0000-000000000002")
            sent-notification (atom nil)]
        ;; Insert a dummy pelada
        (jdbc/execute! ds (hsql/format (-> (h/insert-into :Peladas)
                                           (h/values [{:id pelada-id
                                                       :organization_id org-id
                                                       :status [:cast "attendance" :pelada_status]
                                                       :scheduled_at (java.sql.Timestamp. (System/currentTimeMillis))}]))))

        (with-redefs [notifications/send-notification! (fn [oid type data _]
                                                         (reset! sent-notification {:oid oid :type type :data data}))]
          (let [res (controller.organization/resend-notification org-id "attendance-reminder" pelada-id ds)]
            (is (= "success" (:status res)))
            (is (= :attendance-reminder (:type @sent-notification)))
            (is (= pelada-id (get-in @sent-notification [:data :pelada-id])))
            (is (true? (get-in @sent-notification [:data :force?])))))))))

(deftest send-private-casual-player-notification-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (db.organization/insert-organization {:name "Private Notification Org"} ds)
        pelada-id (parse-uuid "00000000-0000-0000-0000-000000000003")]

    ;; Setup WAHA config enabled for this org
    (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationWahaConfigs)
                                       (h/values [{:organization_id org-id
                                                   :enabled true
                                                   :api_url "http://waha:3000"
                                                   :instance "default"
                                                   :group_id "group123"
                                                   :attendance_reminder_enabled true
                                                   :use_all_mention false}]))))

    ;; Create 3 users:
    ;; U1: diarista, opted in
    ;; U2: convidado, opted out
    ;; U3: mensalista, opted in
    (let [u1-id (db.user/insert-user {:name "OptedIn Diarista" :username "optin_diarista" :phone "5511988888888" :receive-non-mensalista-updates true} ds)
          u2-id (db.user/insert-user {:name "OptedOut Convidado" :username "optout_convidado" :phone "5511977777777" :receive-non-mensalista-updates false} ds)
          u3-id (db.user/insert-user {:name "OptedIn Mensalista" :username "optin_mensalista" :phone "5511966666666" :receive-non-mensalista-updates true} ds)]

;; Add to OrganizationPlayers
      (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationPlayers)
                                         (h/values [{:organization_id org-id :user_id u1-id :member_type [:cast "diarista" :member_type]}
                                                    {:organization_id org-id :user_id u2-id :member_type [:cast "convidado" :member_type]}
                                                    {:organization_id org-id :user_id u3-id :member_type [:cast "mensalista" :member_type]}]))))

      (testing "Private notification sent only to opted-in non-mensalista users"
        (let [sent-calls (atom [])]
          (with-redefs [waha/send-message (fn [config msg mentions]
                                            (swap! sent-calls conj {:config config :msg msg :mentions mentions}))]
            (notifications/send-notification! org-id :new-pelada {:pelada-id pelada-id :scheduled-at "2026-08-20T10:00:00Z" :notify-casual-players true} ds)

            ;; Should have 2 calls: 1 to group, 1 private to u1
            (is (= 2 (count @sent-calls)))
            (let [private-call (second @sent-calls)]
              (is (= "5511988888888@c.us" (get-in private-call [:config :waha-group-id])))
              (is (str/includes? (:msg private-call) "Lista de Presença Aberta!"))))))

      (testing "Private notification suppressed when notify-casual-players is false"
        (let [sent-calls (atom [])]
          (with-redefs [waha/send-message (fn [config msg mentions]
                                            (swap! sent-calls conj {:config config :msg msg :mentions mentions}))]
            (notifications/send-notification! org-id :new-pelada {:pelada-id pelada-id :scheduled-at "2026-08-20T10:00:00Z" :notify-casual-players false} ds)

            ;; Should have only 1 call (the group message)
            (is (= 1 (count @sent-calls)))))))))

