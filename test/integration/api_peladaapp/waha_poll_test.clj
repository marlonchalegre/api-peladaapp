(ns api-peladaapp.waha-poll-test
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

(deftest waha-poll-on-start-test
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)
        org-id (db.organization/insert-organization {:name "Waha Poll Org"} ds)]

    (jdbc/execute! ds (hsql/format (-> (h/insert-into :OrganizationWahaConfigs)
                                       (h/values [{:organization_id org-id
                                                   :enabled true
                                                   :api_url "http://waha:3000"
                                                   :instance "default"
                                                   :group_id "group123"
                                                   :start_msg_enabled true}]))))

    (testing "Send start notification and create poll"
      (let [teams [{:id 1 :name "Time A"} {:id 2 :name "Time B"}]
            team-players [{:player_name "P1" :team_id 1 :position "Striker"}
                          {:player_name "P2" :team_id 2 :position "Goalkeeper"}]
            sent-message (atom nil)
            sent-poll (atom nil)]
        (with-redefs [waha/send-message (fn [_ message _] (reset! sent-message message))
                      waha/send-poll (fn [_ question options multiple?]
                                       (reset! sent-poll {:question question :options options :multiple? multiple?}))]
          (notifications/send-notification! org-id :start {:teams teams :team-players team-players} ds)

          (is (some? @sent-message))
          (is (some? @sent-poll))
          (is (= "Quem será o campeão?" (:question @sent-poll)))
          (is (= ["Time A" "Time B"] (:options @sent-poll)))
          (is (false? (:multiple? @sent-poll))))))))
