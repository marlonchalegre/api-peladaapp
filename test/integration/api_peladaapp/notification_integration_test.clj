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
          (notifications/send-notification! org-id :attendance-reminder {:pending-players pending-players} ds)

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
          (notifications/send-notification! org-id :attendance-reminder {:pending-players pending-players} ds)

          (is (some? @sent-payload))
          (is (= ["5511911111111@c.us"] (:mentions @sent-payload)))
          (is (not (re-find #"@all" (:message @sent-payload)))))))))
