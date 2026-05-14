(ns api-peladaapp.waha-scheduler-reproduction-test
  (:require
   [api-peladaapp.db.pelada :as db.pelada]
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

(deftest test-list-peladas-for-vote-notification-reproduction
  (let [db-val (-> th/*test-system* :database :database)
        ds (if (fn? db-val) (db-val) db-val)]
    (testing "REPRODUCTION: Should return peladas for 30m, 12h and 23h reminders even with ISO-8601 format"
      (let [org-id (:id (exec-one! ds (-> (h/insert-into :Organizations) (h/values [{:name "Org 1"}]) (h/returning :id))))
            now (java.time.OffsetDateTime/now)
            date-31m (.minus now (java.time.Duration/ofMinutes 31))
            date-12-5h (.minus now (java.time.Duration/ofMinutes (+ (* 12 60) 30)))]

        ;; Pelada 1: Closed 12.5h ago, 12h reminder NOT sent -> SHOULD be returned as :vote_12h
        (let [p1-id (db.pelada/insert-pelada {:organization-id org-id} ds)]
          (exec! ds (-> (h/update :Peladas)
                        (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast date-12-5h :timestamp]]})
                        (h/where [:= :id p1-id]))))

        ;; Pelada 2: Closed 31m ago, 30m reminder NOT sent -> SHOULD be returned as :vote_30m
        (let [p2-id (db.pelada/insert-pelada {:organization-id org-id} ds)]
          (exec! ds (-> (h/update :Peladas)
                        (h/set {:status [:cast "closed" :pelada_status] :closed_at [[:cast date-31m :timestamp]]})
                        (h/where [:= :id p2-id]))))

        (let [results (db.pelada/list-peladas-for-vote-reminders ds)]
          (is (some #(= :vote_12h (:type %)) results) "Should find 12h reminder")
          (is (some #(= :vote_30m (:type %)) results) "Should find 30m reminder"))))))
