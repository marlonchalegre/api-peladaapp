(ns api-peladaapp.waha-scheduler-test
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
   [api-peladaapp.helpers.misc :as misc]
   [api-peladaapp.helpers.sql :as hsql]
   [api-peladaapp.test-helpers :as th]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [honey.sql.helpers :as h]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(use-fixtures :once th/test-system-fixture)

(defn- exec-one! [ds query]
  (jdbc/execute-one! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(defn- exec! [ds query] (jdbc/execute! ds (hsql/format query) {:builder-fn rs/as-unqualified-lower-maps}))

(deftest test-list-peladas-for-vote-notification
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    (testing "Should return peladas closed more than 24h ago with message not sent"
      (let [org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org 1"}]) (h/returning :id))))
            now (java.time.OffsetDateTime/now)
            old-date (.minus now (java.time.Duration/ofHours 25))
            recent-date (.minus now (java.time.Duration/ofHours 23))]

        ;; Pelada 1: Closed 25h ago, message NOT sent -> SHOULD be returned
        (let [p1-id (db.pelada/insert-pelada {:organization-id org-id} ds)]
          (exec! ds (-> (h/update :Peladas)
                        (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast old-date :timestamp]]})
                        (h/where [:= :id (misc/as-uuid p1-id)]))))

        ;; Pelada 2: Closed 23h ago, message NOT sent -> SHOULD NOT be returned
        (let [p2-id (db.pelada/insert-pelada {:organization-id org-id} ds)]
          (exec! ds (-> (h/update :Peladas)
                        (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast recent-date :timestamp]]})
                        (h/where [:= :id (misc/as-uuid p2-id)]))))

        ;; Pelada 3: Closed 25h ago, message ALREADY sent -> SHOULD NOT be returned
        (let [p3-id (db.pelada/insert-pelada {:organization-id org-id} ds)]
          (exec! ds (-> (h/update :Peladas)
                        (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast old-date :timestamp]]})
                        (h/where [:= :id (misc/as-uuid p3-id)])))
          (exec! ds (-> (h/insert-into :PeladaReminders) (h/values [{:pelada_id (misc/as-uuid p3-id) :type [:cast "vote_ended" :reminder_type]}]))))

        (let [results (db.pelada/list-peladas-for-vote-notification ds)]
          (is (= 1 (count results)))
          (is (some #(= "Org 1" (:organization-name %)) results)))))

    (testing "Should return peladas for 30m, 12h and 23h reminders"
      (let [org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org 2"}]) (h/returning :id))))
            now (java.time.OffsetDateTime/now)
            date-31m (.minus now (java.time.Duration/ofMinutes 31))
            date-12-5h (.minus now (java.time.Duration/ofMinutes (+ (* 12 60) 30)))
            date-23-5h (.minus now (java.time.Duration/ofMinutes (+ (* 23 60) 30)))]

        ;; Pelada 4: Closed 12.5h ago, 12h reminder NOT sent -> SHOULD be returned as :vote_12h
        (let [p4-id (db.pelada/insert-pelada {:organization-id org-id} ds)]
          (exec! ds (-> (h/update :Peladas)
                        (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast date-12-5h :timestamp]]})
                        (h/where [:= :id (misc/as-uuid p4-id)])))
          (exec! ds (-> (h/insert-into :PeladaReminders) (h/values [{:pelada_id (misc/as-uuid p4-id) :type [:cast "vote_30m" :reminder_type]}]))))

        ;; Pelada 5: Closed 23.5h ago, 23h reminder NOT sent -> SHOULD be returned as :vote_23h
        (let [p5-id (db.pelada/insert-pelada {:organization-id org-id} ds)]
          (exec! ds (-> (h/update :Peladas)
                        (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast date-23-5h :timestamp]]})
                        (h/where [:= :id (misc/as-uuid p5-id)])))
          (exec! ds (-> (h/insert-into :PeladaReminders) (h/values [{:pelada_id (misc/as-uuid p5-id) :type [:cast "vote_30m" :reminder_type]}
                                                                    {:pelada_id (misc/as-uuid p5-id) :type [:cast "vote_12h" :reminder_type]}]))))

        ;; Pelada 6: Closed 31m ago, 30m reminder NOT sent -> SHOULD be returned as :vote_30m
        (let [p6-id (db.pelada/insert-pelada {:organization-id org-id} ds)]
          (exec! ds (-> (h/update :Peladas)
                        (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast date-31m :timestamp]]})
                        (h/where [:= :id (misc/as-uuid p6-id)]))))

        (let [results (db.pelada/list-peladas-for-vote-reminders ds)
              p4-rem (first (filter #(and (= "Org 2" (:organization-name (:pelada %))) (= :vote_12h (:type %))) results))
              p5-rem (first (filter #(and (= "Org 2" (:organization-name (:pelada %))) (= :vote_23h (:type %))) results))
              p6-rem (first (filter #(and (= "Org 2" (:organization-name (:pelada %))) (= :vote_30m (:type %))) results))]
          (is (some? p4-rem))
          (is (= :vote_12h (:type p4-rem)))
          (is (some? p5-rem))
          (is (= :vote_23h (:type p5-rem)))
          (is (some? p6-rem))
          (is (= :vote_30m (:type p6-rem))))))))
